package com.example.chat.core.theme

/** One selectable choice per broad visual silhouette. Older IDs are retained and
 * map here without rewriting saved preferences. Names describe local drawings,
 * not a claim of original branded artwork. */
fun canonicalDeliveryStyle(style: DeliveryIconStyle): DeliveryIconStyle = when (style) {
    DeliveryIconStyle.stock, DeliveryIconStyle.rcIos11, DeliveryIconStyle.bbmV2, DeliveryIconStyle.bwTicks, DeliveryIconStyle.greenTick, DeliveryIconStyle.ios2, DeliveryIconStyle.rcTick, DeliveryIconStyle.allo, DeliveryIconStyle.altCrayonV2, DeliveryIconStyle.altCrayon, DeliveryIconStyle.coalfield, DeliveryIconStyle.grw, DeliveryIconStyle.gbIos, DeliveryIconStyle.joaquins -> DeliveryIconStyle.stock
    DeliveryIconStyle.sticker, DeliveryIconStyle.cirCheck, DeliveryIconStyle.circlePrint, DeliveryIconStyle.gabCircle, DeliveryIconStyle.gabFace, DeliveryIconStyle.gabIflo, DeliveryIconStyle.letterCircle, DeliveryIconStyle.rcAlo, DeliveryIconStyle.vantCircle, DeliveryIconStyle.trafficLights, DeliveryIconStyle.glowingDots, DeliveryIconStyle.material3Rounded, DeliveryIconStyle.alien, DeliveryIconStyle.bbm, DeliveryIconStyle.ngAndroidBot, DeliveryIconStyle.bpgCircle, DeliveryIconStyle.gbCircles, DeliveryIconStyle.samyadeepCircles, DeliveryIconStyle.colorSimple, DeliveryIconStyle.ctoon, DeliveryIconStyle.flatCircles, DeliveryIconStyle.gifcon, DeliveryIconStyle.sidzyHd, DeliveryIconStyle.insect, DeliveryIconStyle.jalopy, DeliveryIconStyle.jokerTraffic, DeliveryIconStyle.materialV2, DeliveryIconStyle.messenger, DeliveryIconStyle.minions, DeliveryIconStyle.newWaca, DeliveryIconStyle.nhsc, DeliveryIconStyle.nazaNh, DeliveryIconStyle.nlCircle, DeliveryIconStyle.pacman, DeliveryIconStyle.pokemon, DeliveryIconStyle.sDot, DeliveryIconStyle.smile, DeliveryIconStyle.timeBomb, DeliveryIconStyle.jokerTraffic2, DeliveryIconStyle.pointer, DeliveryIconStyle.ticTicCircle2, DeliveryIconStyle.kitty, DeliveryIconStyle.flower, DeliveryIconStyle.quinn, DeliveryIconStyle.x -> DeliveryIconStyle.sticker
    DeliveryIconStyle.triangle -> DeliveryIconStyle.triangle
    DeliveryIconStyle.stars, DeliveryIconStyle.star -> DeliveryIconStyle.stars
    DeliveryIconStyle.hearts, DeliveryIconStyle.svHeart, DeliveryIconStyle.neyHeart -> DeliveryIconStyle.hearts
    DeliveryIconStyle.batman, DeliveryIconStyle.bat, DeliveryIconStyle.wings -> DeliveryIconStyle.batman
    DeliveryIconStyle.doubleSword -> DeliveryIconStyle.doubleSword
    DeliveryIconStyle.aran, DeliveryIconStyle.blend, DeliveryIconStyle.eclipsis, DeliveryIconStyle.faceSwitch, DeliveryIconStyle.jessy, DeliveryIconStyle.loveSwitch, DeliveryIconStyle.materialSwitch, DeliveryIconStyle.oldWaca, DeliveryIconStyle.seasons, DeliveryIconStyle.flatSwitch, DeliveryIconStyle.wbg -> DeliveryIconStyle.aran
    DeliveryIconStyle.bar -> DeliveryIconStyle.bar
    DeliveryIconStyle.feet -> DeliveryIconStyle.feet
    DeliveryIconStyle.letter, DeliveryIconStyle.google, DeliveryIconStyle.googleV2, DeliveryIconStyle.hike -> DeliveryIconStyle.letter
    DeliveryIconStyle.inbox -> DeliveryIconStyle.inbox
    DeliveryIconStyle.like, DeliveryIconStyle.mickeyHands -> DeliveryIconStyle.like
    DeliveryIconStyle.pixelated -> DeliveryIconStyle.pixelated
    DeliveryIconStyle.snapChat -> DeliveryIconStyle.snapChat
    DeliveryIconStyle.treble -> DeliveryIconStyle.treble
    DeliveryIconStyle.twitterConcept -> DeliveryIconStyle.twitterConcept
    DeliveryIconStyle.iceCream -> DeliveryIconStyle.iceCream
    DeliveryIconStyle.pony -> DeliveryIconStyle.pony
}

val uniqueDeliveryStyles: List<DeliveryIconStyle> = listOf(
    DeliveryIconStyle.stock,
    DeliveryIconStyle.sticker,
    DeliveryIconStyle.triangle,
    DeliveryIconStyle.stars,
    DeliveryIconStyle.hearts,
    DeliveryIconStyle.batman,
    DeliveryIconStyle.doubleSword,
    DeliveryIconStyle.aran,
    DeliveryIconStyle.bar,
    DeliveryIconStyle.feet,
    DeliveryIconStyle.letter,
    DeliveryIconStyle.inbox,
    DeliveryIconStyle.like,
    DeliveryIconStyle.pixelated,
    DeliveryIconStyle.snapChat,
    DeliveryIconStyle.treble,
    DeliveryIconStyle.twitterConcept,
    DeliveryIconStyle.iceCream,
    DeliveryIconStyle.pony,
)

fun deliveryStyleLabel(style: DeliveryIconStyle): String = when (canonicalDeliveryStyle(style)) {
    DeliveryIconStyle.stock -> "Classic checks"
    DeliveryIconStyle.sticker -> "Circle"
    DeliveryIconStyle.triangle -> "Triangle"
    DeliveryIconStyle.stars -> "Stars"
    DeliveryIconStyle.hearts -> "Hearts"
    DeliveryIconStyle.batman -> "Winged bat"
    DeliveryIconStyle.doubleSword -> "Crossed swords"
    DeliveryIconStyle.aran -> "Capsule switches"
    DeliveryIconStyle.bar -> "Signal bars"
    DeliveryIconStyle.feet -> "Footprints"
    DeliveryIconStyle.letter -> "Letter R"
    DeliveryIconStyle.inbox -> "Envelopes"
    DeliveryIconStyle.like -> "Thumbs up"
    DeliveryIconStyle.pixelated -> "Pixel mosaic"
    DeliveryIconStyle.snapChat -> "Ghosts"
    DeliveryIconStyle.treble -> "Zigzags"
    DeliveryIconStyle.twitterConcept -> "Birds"
    DeliveryIconStyle.iceCream -> "Ice cream"
    DeliveryIconStyle.pony -> "Pony"
    else -> error("Unmapped canonical delivery style")
}
