package com.slide.app

/**
 * The two paragraphs the About card shows above the licence block.
 *
 * They are values rather than literals inside the Compose tree so the attribution can be asserted
 * as a value: the fork note must keep naming Slide and its licence, and the pitch must describe
 * what this fork actually does. Apache-2.0 requires the first; a fork that still reads as the app
 * it forked fails the second.
 */
object AboutCopy {
    /** What Polykeyboard is, in the order a person would notice it. */
    const val PITCH =
        "Polykeyboard - hexagonal keys, a two-handed split layout in landscape, " +
            "adjustable key size, colour control and offline voice typing."

    /** The fork note. Slide is named here on purpose: this is the attribution, not branding. */
    const val FORK_NOTE =
        "This is a fork. Polykeyboard is built on Slide (github.com/Robertg761/Slide), " +
            "licensed under the Apache License 2.0; the original work and its notices are kept " +
            "in full. Everything on top of it was built by Reck1355 with JadeSoul."
}
