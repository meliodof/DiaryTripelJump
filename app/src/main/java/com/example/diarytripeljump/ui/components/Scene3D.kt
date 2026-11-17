package com.example.diarytripeljump.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Простая трёхмерная сцена на Canvas без сторонних библиотек: перспективная камера, которую можно вращать
 * пальцем, грани с освещением и отрисовка «от дальнего к ближнему». Используется в 3D-графике и мишени разбега.
 */

/** Точка мира: x — вправо, y — вверх, z — вглубь. */
data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun normalized(): V3 { val l = sqrt(dot(this)).coerceAtLeast(1e-6f); return V3(x / l, y / l, z / l) }
}

/**
 * Камера: поворот вокруг вертикали [yaw] и наклон [pitch] (градусы), расстояние до центра [distance].
 * [scale] — масштаб на экране, [center] — куда на экране проецируется центр сцены.
 */
class Camera3D(yaw: Float, pitch: Float, private val distance: Float, private val scale: Float, private val center: Offset) {
    private val cy = cos(yaw * PI / 180).toFloat()
    private val sy = sin(yaw * PI / 180).toFloat()
    private val cp = cos(pitch * PI / 180).toFloat()
    private val sp = sin(pitch * PI / 180).toFloat()

    /** Поворот мира под камеру (без сдвига): так поворачиваются и нормали граней. */
    fun rotate(p: V3): V3 {
        val x1 = p.x * cy - p.z * sy
        val z1 = p.x * sy + p.z * cy
        val y2 = p.y * cp + z1 * sp
        val z2 = -p.y * sp + z1 * cp
        return V3(x1, y2, z2)
    }

    /** Координаты относительно камеры: z — расстояние от неё. */
    fun view(p: V3): V3 = rotate(p).let { V3(it.x, it.y, it.z + distance) }

    /** Грань повёрнута к камере: нормаль смотрит против луча от камеры к грани. */
    fun facing(center: V3, normal: V3): Boolean = rotate(normal).dot(view(center)) < 0f

    fun project(p: V3): Offset {
        val v = view(p)
        val d = max(v.z, 0.05f)
        return Offset(center.x + v.x * scale / d, center.y - v.y * scale / d)
    }

    fun depth(p: V3): Float = view(p).z
}

/** Грань: вершины по кругу, нормаль наружу, цвет и признак обводки. */
class Face3D(val points: List<V3>, val normal: V3, val color: Color, val outline: Color? = null) {
    val center: V3 = V3(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat(), points.map { it.z }.average().toFloat())
}

/** Свет сверху-спереди: грани, повёрнутые к нему, светлее. */
private val LIGHT = V3(-0.35f, 1f, -0.55f).normalized()

fun shade(color: Color, normal: V3): Color {
    val k = 0.55f + 0.45f * max(0f, normal.dot(LIGHT))
    return Color(min(1f, color.red * k), min(1f, color.green * k), min(1f, color.blue * k), color.alpha)
}

/** Прямоугольный брус от [a] до [b]: шесть граней. */
fun box(a: V3, b: V3, color: Color, outline: Color? = null): List<Face3D> {
    val x0 = min(a.x, b.x); val x1 = max(a.x, b.x)
    val y0 = min(a.y, b.y); val y1 = max(a.y, b.y)
    val z0 = min(a.z, b.z); val z1 = max(a.z, b.z)
    fun f(n: V3, vararg p: V3) = Face3D(p.toList(), n, color, outline)
    return listOf(
        f(V3(0f, 1f, 0f), V3(x0, y1, z0), V3(x1, y1, z0), V3(x1, y1, z1), V3(x0, y1, z1)),   // верх
        f(V3(0f, -1f, 0f), V3(x0, y0, z0), V3(x0, y0, z1), V3(x1, y0, z1), V3(x1, y0, z0)),  // низ
        f(V3(0f, 0f, -1f), V3(x0, y0, z0), V3(x1, y0, z0), V3(x1, y1, z0), V3(x0, y1, z0)),  // перед
        f(V3(0f, 0f, 1f), V3(x0, y0, z1), V3(x0, y1, z1), V3(x1, y1, z1), V3(x1, y0, z1)),   // зад
        f(V3(-1f, 0f, 0f), V3(x0, y0, z0), V3(x0, y1, z0), V3(x0, y1, z1), V3(x0, y0, z1)),  // лево
        f(V3(1f, 0f, 0f), V3(x1, y0, z0), V3(x1, y0, z1), V3(x1, y1, z1), V3(x1, y1, z0))    // право
    )
}

/** Плоский прямоугольник на земле (y = [y]). */
fun floorQuad(x0: Float, z0: Float, x1: Float, z1: Float, y: Float, color: Color): Face3D =
    Face3D(listOf(V3(x0, y, z0), V3(x1, y, z0), V3(x1, y, z1), V3(x0, y, z1)), V3(0f, 1f, 0f), color)

/**
 * Рисует грани: только повёрнутые к камере, от дальних к ближним — ближние закрывают дальние.
 * [shaded] — освещать по нормали (для плоских цветных подложек можно выключить).
 */
fun DrawScope.drawFaces(camera: Camera3D, faces: List<Face3D>, shaded: Boolean = true) {
    faces.map { f -> f to camera.depth(f.center) }
        // плоские подложки видны всегда, у объёмных граней — только повёрнутые к камере
        .filter { (f, _) -> !shaded || camera.facing(f.center, f.normal) }
        .sortedByDescending { it.second }
        .forEach { (f, _) ->
            val path = Path()
            f.points.forEachIndexed { i, v -> val o = camera.project(v); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            path.close()
            drawPath(path, if (shaded) shade(f.color, f.normal) else f.color)
            f.outline?.let { drawPath(path, it, style = Stroke(1f)) }
        }
}

/** Шар: круг с бликом, размер по перспективе. */
fun DrawScope.drawSphere(camera: Camera3D, at: V3, radius: Float, color: Color, ring: Color? = null) {
    val c = camera.project(at)
    val edge = camera.project(V3(at.x + radius, at.y, at.z))
    val r = max(2f, kotlin.math.abs(edge.x - c.x))
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), color, shade(color, V3(0f, -1f, 0f))), center = Offset(c.x - r * 0.35f, c.y - r * 0.35f), radius = r * 1.6f),
        radius = r, center = c
    )
    ring?.let { drawCircle(it, radius = r + 3f, center = c, style = Stroke(2.5f)) }
}
