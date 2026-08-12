package dev.naicompanion.app.core.prompt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtistStrengthTest {

    @Test
    fun `tiers match the nax vote thresholds`() {
        assertThat(ArtistStrength.fromVotes(20, 25)).isEqualTo(ArtistStrength.STRONG)
        assertThat(ArtistStrength.fromVotes(8, 10)).isEqualTo(ArtistStrength.SOLID)
        assertThat(ArtistStrength.fromVotes(1, 10)).isEqualTo(ArtistStrength.MIXED)
        assertThat(ArtistStrength.fromVotes(-5, 8)).isEqualTo(ArtistStrength.WEAK)
    }

    @Test
    fun `too few votes are treated as unknown`() {
        assertThat(ArtistStrength.fromVotes(100, 2)).isEqualTo(ArtistStrength.UNKNOWN)
        assertThat(ArtistStrength.fromVotes(null, null)).isEqualTo(ArtistStrength.UNKNOWN)
        assertThat(ArtistStrength.fromVotes(5, null)).isEqualTo(ArtistStrength.UNKNOWN)
    }

    @Test
    fun `recommended weights only nudge mixed tags`() {
        assertThat(ArtistStrength.STRONG.recommendedWeight).isNull()
        assertThat(ArtistStrength.SOLID.recommendedWeight).isNull()
        assertThat(ArtistStrength.MIXED.recommendedWeight).isEqualTo(1.1)
        assertThat(ArtistStrength.WEAK.recommendedWeight).isNull()
        assertThat(ArtistStrength.UNKNOWN.recommendedWeight).isNull()
    }

    @Test
    fun `rank score shrinks low vote noise`() {
        val noisy = strengthRankScore(score = 5, votes = 3)
        val solid = strengthRankScore(score = 5, votes = 40)
        assertThat(solid).isGreaterThan(noisy)
        assertThat(strengthRankScore(null, null)).isNegativeInfinity()
    }
}
