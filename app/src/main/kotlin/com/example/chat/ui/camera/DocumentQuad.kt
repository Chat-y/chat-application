package com.example.chat.ui.camera

import kotlin.math.*

/** Minimum paper footprint in normalized frame coordinates (CAM-R4-024). */
const val MIN_PAPER_AREA_FRACTION = 0.05

data class PaperPoint(val x: Float, val y: Float)
data class PaperQuad(val topLeft: PaperPoint, val topRight: PaperPoint, val bottomRight: PaperPoint, val bottomLeft: PaperPoint) {
    val points: List<PaperPoint> get() = listOf(topLeft,topRight,bottomRight,bottomLeft)
    /** Shoelace area, independent of preview/capture pixel resolution. Non-finite is invalid. */
    val areaFraction: Double get() {
        val p = points
        if (p.any { !it.x.isFinite() || !it.y.isFinite() }) return 0.0
        return abs(p.indices.sumOf { i ->
            val a = p[i]; val b = p[(i + 1) % p.size]
            a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }) / 2.0
    }
    fun isValid(): Boolean {
        if (points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) return false
        val crosses=points.indices.map { i -> val a=points[i]; val b=points[(i+1)%4]; val c=points[(i+2)%4]; (b.x-a.x)*(c.y-b.y)-(b.y-a.y)*(c.x-b.x) }
        if (crosses.any { it <= .0001f }) return false
        // Original threshold allowed a paper occupying only 2% of the frame:
        // val area=abs(points.indices.sumOf { i -> val a=points[i];val b=points[(i+1)%4];(a.x*b.y-b.x*a.y).toDouble() })/2
        // return area >= .02
        return areaFraction >= MIN_PAPER_AREA_FRACTION
    }
    fun moved(index: Int, point: PaperPoint): PaperQuad {
        require(index in 0..3)
        val p=points.toMutableList();p[index]=PaperPoint(point.x.coerceIn(0f,1f),point.y.coerceIn(0f,1f))
        return PaperQuad(p[0],p[1],p[2],p[3])
    }
    companion object { val inset = PaperQuad(PaperPoint(.08f,.08f),PaperPoint(.92f,.08f),PaperPoint(.92f,.92f),PaperPoint(.08f,.92f)) }
}

/** Conservative bright-paper boundary proposal on a small luminance raster.
 * Not an acceptance decision. A user must inspect/adjust corners before crop. */
fun detectPaperQuad(luma: IntArray,width: Int,height: Int): PaperQuad? {
    require(width>0 && height>0 && luma.size==width*height)
    val sorted=luma.sorted();val threshold=max(130,sorted[(sorted.size*0.65).toInt().coerceAtMost(sorted.lastIndex)])
    val pts=ArrayList<PaperPoint>()
    for(y in 1 until height-1) for(x in 1 until width-1) {
        val v=luma[y*width+x]
        if(v>=threshold && (abs(v-luma[y*width+x-1])>28 || abs(v-luma[(y-1)*width+x])>28 || abs(v-luma[y*width+x+1])>28 || abs(v-luma[(y+1)*width+x])>28)) pts.add(PaperPoint(x.toFloat()/(width-1),y.toFloat()/(height-1)))
    }
    if(pts.size<12) return null
    val tl=pts.minBy {it.x+it.y};val br=pts.maxBy {it.x+it.y};val tr=pts.maxBy {it.x-it.y};val bl=pts.minBy {it.x-it.y}
    return PaperQuad(tl,tr,br,bl).takeIf {it.isValid()}
}
