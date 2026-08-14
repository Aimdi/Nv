import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import '../prompt/artist_mix_engine.dart';
import '../prompt/style_knowledge.dart';
import 'nax_strength_service.dart';
import 'style_advisor_service.dart';
import 'style_match_service.dart';

class NvKnowledgeRequest {
  const NvKnowledgeRequest({
    required this.method,
    required this.path,
    this.query = const {},
    this.headers = const {},
    this.body,
  });

  final String method;
  final String path;
  final Map<String, String> query;
  final Map<String, String> headers;
  final Uint8List? body;
}

class NvKnowledgeResponse {
  const NvKnowledgeResponse({
    required this.status,
    required this.body,
    this.contentType = 'application/json; charset=utf-8',
  });

  final int status;
  final Object body;
  final String contentType;

  String get text => body is String ? body as String : jsonEncode(body);
}

/// Local HTTP API so Grok (or any tool) can use the same knowledge Nv does.
///
/// Binds 127.0.0.1 only. Optional bearer token.
class NvKnowledgeApi {
  NvKnowledgeApi({
    StyleMatchService? matcher,
    StyleAdvisorService? advisor,
    this.xaiKeyProvider,
  })  : _matcher = matcher ?? StyleMatchService(),
        _advisor = advisor ?? StyleAdvisorService();

  final StyleMatchService _matcher;
  final StyleAdvisorService _advisor;
  Future<String> Function()? xaiKeyProvider;

  static NvKnowledgeApi? _instance;
  static NvKnowledgeApi get instance => _instance ??= NvKnowledgeApi();

  HttpServer? _server;
  String? token;
  int get port => _server?.port ?? 0;
  bool get isRunning => _server != null;

  Future<void> start({int port = 8765, String? token}) async {
    await stop();
    this.token = (token == null || token.isEmpty) ? null : token;
    _instance = this;
    _server = await HttpServer.bind(InternetAddress.loopbackIPv4, port);
    unawaited(_serve());
  }

  Future<void> stop() async {
    await _server?.close(force: true);
    _server = null;
  }

  Future<void> _serve() async {
    final server = _server;
    if (server == null) return;
    await for (final req in server) {
      try {
        await _write(req, await handle(await _fromHttp(req)));
      } catch (error) {
        try {
          await _write(
            req,
            NvKnowledgeResponse(
              status: 500,
              body: {'error': error.toString()},
            ),
          );
        } catch (_) {}
      }
    }
  }

  static Future<NvKnowledgeRequest> _fromHttp(HttpRequest req) async {
    final query = <String, String>{};
    req.uri.queryParameters.forEach((k, v) => query[k] = v);
    final headers = <String, String>{};
    req.headers.forEach((name, values) {
      if (values.isNotEmpty) headers[name.toLowerCase()] = values.first;
    });
    final builder = BytesBuilder(copy: false);
    await for (final chunk in req) {
      builder.add(chunk);
    }
    return NvKnowledgeRequest(
      method: req.method.toUpperCase(),
      path: req.uri.path,
      query: query,
      headers: headers,
      body: builder.isEmpty ? null : builder.takeBytes(),
    );
  }

  static Future<void> _write(HttpRequest req, NvKnowledgeResponse res) async {
    req.response.statusCode = res.status;
    req.response.headers.set('Content-Type', res.contentType);
    req.response.headers.set('Access-Control-Allow-Origin', '*');
    req.response.headers.set('Access-Control-Allow-Headers', 'Authorization, Content-Type');
    req.response.headers.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    req.response.write(res.text);
    await req.response.close();
  }

  Future<NvKnowledgeResponse> handle(NvKnowledgeRequest req) async {
    if (req.method == 'OPTIONS') {
      return const NvKnowledgeResponse(status: 204, body: '');
    }
    if (token != null) {
      final auth = req.headers['authorization'] ?? '';
      if (auth != 'Bearer $token') {
        return const NvKnowledgeResponse(
          status: 401,
          body: {'error': 'Bearer token required'},
        );
      }
    }

    final path = req.path.endsWith('/') && req.path.length > 1
        ? req.path.substring(0, req.path.length - 1)
        : req.path;

    await Future.wait([
      NaxStrengthCatalog.load(),
      ArtistMixEngine.loadCatalog(),
      StyleFingerprintIndex.load(),
    ]);

    if (req.method == 'GET' && (path == '/v1/health' || path == '/health')) {
      return NvKnowledgeResponse(
        status: 200,
        body: {
          'ok': true,
          'app': 'Nv',
          'version': StyleKnowledge.version,
          'nax_artists': NaxStrengthCatalog.instance.size,
          'fingerprints': StyleFingerprintIndex.instance.size,
        },
      );
    }
    if (req.method == 'GET' && path == '/v1/knowledge') {
      return NvKnowledgeResponse(status: 200, body: StyleKnowledge.catalogPayload());
    }
    if (req.method == 'GET' && path == '/v1/openapi.json') {
      return NvKnowledgeResponse(status: 200, body: openApi);
    }
    if (req.method == 'GET' && path == '/v1/artists') {
      return _artists(req.query);
    }
    if (req.method == 'GET' && path.startsWith('/v1/artists/')) {
      final tag = Uri.decodeComponent(path.substring('/v1/artists/'.length));
      final entry = NaxStrengthCatalog.instance.lookup(tag);
      if (entry == null) {
        return const NvKnowledgeResponse(status: 404, body: {'error': 'unknown artist'});
      }
      return NvKnowledgeResponse(
        status: 200,
        body: StyleKnowledge.artistPayload(NaxStrengthCatalog.normalizeTag(tag), entry),
      );
    }
    if (req.method == 'POST' &&
        (path == '/v1/style/match' || path == '/v1/style/advise')) {
      return _style(req, advise: path.endsWith('/advise'));
    }

    return const NvKnowledgeResponse(
      status: 404,
      body: {
        'error': 'not found',
        'hint': 'GET /v1/health /v1/knowledge /v1/artists?q= /v1/openapi.json ; '
            'POST /v1/style/match /v1/style/advise',
      },
    );
  }

  NvKnowledgeResponse _artists(Map<String, String> query) {
    final q = query['q'] ?? query['query'] ?? '';
    final limit = int.tryParse(query['limit'] ?? '') ?? 20;
    final rows = NaxStrengthCatalog.instance.search(q, limit: limit.clamp(1, 80));
    return NvKnowledgeResponse(
      status: 200,
      body: {
        'query': q,
        'artists': [
          for (final row in rows) StyleKnowledge.artistPayload(row.key, row.value),
        ],
      },
    );
  }

  Future<NvKnowledgeResponse> _style(
    NvKnowledgeRequest req, {
    required bool advise,
  }) async {
    final bytes = _imageFrom(req);
    if (bytes == null || bytes.isEmpty) {
      return const NvKnowledgeResponse(
        status: 400,
        body: {
          'error': 'missing image',
          'hint': 'POST JSON {"image_base64":"..."} or raw image bytes',
        },
      );
    }
    final reverse = (req.query['iqdb'] ?? '1') != '0';
    final report = await _matcher.match(bytes, reverseSearch: reverse);
    final payload = <String, dynamic>{
      'brief': StyleKnowledge.briefFromReport(report),
      'mix': report.mix,
    };
    if (advise) {
      final key = (await xaiKeyProvider?.call())?.trim() ?? '';
      if (key.isEmpty) {
        payload['advice'] = null;
        payload['advice_error'] =
            'No xAI key. Nv still returned the local brief — paste it into Grok, '
            'or set an xAI key and POST /v1/style/advise again.';
      } else {
        try {
          final advice = await _advisor.advise(
            report: report,
            apiKey: key,
            imageBytes: bytes,
          );
          payload['advice'] = {
            'mix': advice.mix,
            'model': advice.model,
            'grounded': advice.grounded,
            'picks': [
              for (final p in advice.picks)
                {
                  'name': ArtistMixEngine.promptName(p.name),
                  'role': p.role.name,
                  'reason': p.reason,
                },
            ],
            'notes': advice.notes,
          };
        } catch (error) {
          payload['advice'] = null;
          payload['advice_error'] = error.toString();
        }
      }
    }
    return NvKnowledgeResponse(status: 200, body: payload);
  }

  static Uint8List? _imageFrom(NvKnowledgeRequest req) {
    final body = req.body;
    if (body == null || body.isEmpty) return null;
    final type = req.headers['content-type'] ?? '';
    if (type.contains('application/json') || body.isNotEmpty && body[0] == 0x7b) {
      try {
        final json = jsonDecode(utf8.decode(body));
        if (json is Map && json['image_base64'] is String) {
          return base64Decode((json['image_base64'] as String).replaceAll(RegExp(r'\s'), ''));
        }
      } catch (_) {}
    }
    return body;
  }

  static const openApi = {
    'openapi': '3.0.3',
    'info': {
      'title': 'Nv knowledge API',
      'version': StyleKnowledge.version,
      'description':
          'Local loopback API. Same V4.5 artist knowledge Nv uses: nax style-pull, '
          'seed triples, glue, style-from-image planner. For Grok or any tool.',
    },
    'servers': [
      {'url': 'http://127.0.0.1:8765'},
    ],
    'paths': {
      '/v1/health': {
        'get': {'summary': 'Liveness + catalog sizes'},
      },
      '/v1/knowledge': {
        'get': {'summary': 'Rules, glue, buckets, community triples'},
      },
      '/v1/artists': {
        'get': {'summary': 'Search nax V4.5 strength (q, limit)'},
      },
      '/v1/style/match': {
        'post': {'summary': 'Analyze an image with Nv’s planner (JSON image_base64)'},
      },
      '/v1/style/advise': {
        'post': {'summary': 'Same as match, then Grok refined against Nv’s catalog'},
      },
    },
  };
}
