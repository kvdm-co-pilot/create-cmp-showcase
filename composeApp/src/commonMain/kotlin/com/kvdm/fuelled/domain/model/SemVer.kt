package com.kvdm.fuelled.domain.model

/**
 * A `major.minor.patch` version, ordered NUMERICALLY (UPD-02).
 *
 * The point of this type is the ordering, and the ordering is the whole reason UPD-02 exists.
 * Compared as strings, `"0.10.0" < "0.9.0"` — because `'1' < '9'` — so an app that ranked
 * releases lexically would refuse to offer 0.10.0 to someone on 0.9.0, silently, forever. Held
 * as three integers and compared component by component, 0.10.0 is correctly newer.
 *
 * UPD-02 originally required the ordering to ride an Android `versionCode` in the asset
 * filename, precisely to avoid parsing version strings. That was the safer rule and the wrong
 * one for this project: all five published releases are named `fuelled-<semver>.apk`, so the
 * feature would never have matched a real asset. The rule bends to the convention actually in
 * use; what it does NOT bend on is comparing strings, which is what this type prevents.
 *
 * Deliberately not full semver: no pre-release, no build metadata, no ranges. Three integers is
 * what the release names carry and what the comparison needs, and a parser that accepted more
 * would be inventing an ordering for shapes nothing in this project publishes.
 */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

    /** `0.6.0` — how a version is written wherever a human reads one. */
    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /**
         * Exactly three dot-separated integers, or null.
         *
         * Null is not an error here: it is UPD-04's "not an installable asset" and UPD-03's
         * "nothing to compare against". A caller that cannot parse a version has no business
         * guessing one — offering an update on a version it could not read is how a downgrade
         * ships.
         */
        fun parse(raw: String?): SemVer? {
            val parts = raw?.trim()?.split('.') ?: return null
            if (parts.size != 3) return null
            val nums = parts.map { it.toIntOrNull() ?: return null }
            if (nums.any { it < 0 }) return null
            return SemVer(nums[0], nums[1], nums[2])
        }
    }
}
