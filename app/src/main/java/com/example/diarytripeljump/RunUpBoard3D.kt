package com.example.diarytripeljump

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.example.diarytripeljump.data.RunUpShot
import com.example.diarytripeljump.data.formatNumber
import com.example.diarytripeljump.ui.components.V3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * «Попадания на разбеге» в 3D: вырезанный кусок дорожки (тартан на асфальтовом основании) с бруском и пластилиновым
 * валиком — больше в сцене ничего нет. Каждая попытка — чёрный след шиповки: резина передней части стопы и проколы
 * шипов, на пластилине — вмятина. Текстуры строятся процедурно (шум), перспектива — проективное наложение текстуры
 * на каждый участок плоскости (Matrix.setPolyToPoly), без сторонних библиотек и файлов.
 *
 * Мир в метрах: x — поперёк дорожки, y — вверх, z — по ходу разбега. Брусок: z от 0 до 0,20; линия заступа —
 * z = 0,20; пластилин — от 0,20 до 0,30. Носок следа ставится туда, куда пришлась нога: заступ на N стоп —
 * на N стоп за линией заступа, недоступ на N стоп — на N стоп до начала бруска.
 */

/** Длина «стопы» разбега в метрах: в дневнике отклонение вводится в стопах спортсмена. */
private const val FOOT = 0.30f
private const val BOARD_HALF = 0.61f
private const val LINE = 0.05f
private const val SLAB_HALF = 0.80f
private const val TARTAN = 0.013f
private const val BASE = 0.075f
private const val PLAST_Y = 0.007f
private const val NEAR = 0.05f
private const val MARK_W = 0.095f
private const val MARK_L = 0.17f

/** Где на дорожке носок толчковой ноги при отклонении [feet] стоп. */
fun toeZ(feet: Double): Float = when {
    feet > 0 -> 0.20f + feet.toFloat() * FOOT
    feet < 0 -> feet.toFloat() * FOOT
    else -> 0.13f
}

// ------------------------------------------------------------------ процедурные текстуры

private fun hash(x: Int, y: Int, s: Int): Float {
    var h = x * 374761393 + y * 668265263 + s * 982451653
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0x7fffffff) / 2147483647f
}

/** Значение шума с периодом [px] × [py] клеток решётки — текстуры-плитки стыкуются без шва. */
private fun vnoise(x: Float, y: Float, px: Int, py: Int, s: Int): Float {
    val xi = floor(x).toInt(); val yi = floor(y).toInt()
    val fx = x - xi; val fy = y - yi
    fun h(a: Int, b: Int) = hash(Math.floorMod(a, px), Math.floorMod(b, py), s)
    val u = fx * fx * (3 - 2 * fx); val v = fy * fy * (3 - 2 * fy)
    val a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * u
    val b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * u
    return a + (b - a) * v
}

private fun fbm(x: Float, y: Float, px: Int, py: Int, s: Int, octaves: Int = 4): Float {
    var sum = 0f; var amp = 0.5f; var f = 1f; var norm = 0f
    for (o in 0 until octaves) {
        sum += amp * vnoise(x * f, y * f, px * f.toInt(), py * f.toInt(), s + o * 17)
        norm += amp; amp *= 0.5f; f *= 2f
    }
    return sum / norm
}

private fun argb(a: Float, r: Float, g: Float, b: Float): Int {
    fun c(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    return (c(a) shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
}

private fun bitmap(w: Int, h: Int, px: IntArray): Bitmap =
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, w, 0, 0, w, h); setHasMipMap(true) }

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/**
 * Контур передней части подошвы шиповки (0 — снаружи, 1 — внутри): скруглённый носок, смещённый к большому
 * пальцу, и сужение к своду стопы с внутренней стороны. Носок вверху картинки, край слегка неровный.
 */
private fun forefoot(x: Float, y: Float, w: Int, h: Int): Float {
    val u = x / w; val v = y / h
    val waist = smooth(0.55f, 1f, v)
    val cu = 0.5f - 0.06f * (1 - v) + 0.06f * waist
    val front = if (v > 0.35f) 0.43f else 0.43f * sqrt((1 - ((0.35f - v) / 0.35f).let { it * it }).coerceAtLeast(0f))
    val hw = front - 0.06f * waist + 0.06f * (fbm(x / 10f, y / 10f, 13, 20, 57, 2) - 0.5f)
    return smooth(0f, 0.035f, hw - abs(u - cu)) * smooth(0f, 0.03f, v)
}

/** Проколы шипов шиповки для прыжков: доли ширины и длины отпечатка, подковой вокруг подушечки стопы. */
private val SPIKES = listOf(
    0.27f to 0.19f, 0.50f to 0.09f, 0.72f to 0.21f, 0.17f to 0.45f, 0.81f to 0.45f, 0.31f to 0.63f, 0.69f to 0.63f
)

/** Сколько разных следов: попытки не выглядят одним штампом. */
private const val MARK_VARIANTS = 4

/**
 * Прозрачность следа шиповки варианта [variant] (носок вверху картинки). Так выглядит брусок после тренировки:
 * чёрный мазок резины под подушечкой стопы с неровным краем и тонкими полосами вдоль толчка, слабее — под носком,
 * короткий смаз назад, к разбегу, и точки проколов шипов подковой вокруг; часть шипов следа не оставляет.
 */
private fun markAlpha(variant: Int, mw: Int, mh: Int): FloatArray {
    val s = 61 + variant * 13
    fun r(i: Int) = hash(variant, i, s)
    fun sq(v: Float) = v * v
    val ub = 0.5f + 0.10f * (r(1) - 0.5f); val vb = 0.40f + 0.08f * (r(2) - 0.5f)
    val lumpU = ub + 0.16f * (r(5) - 0.5f)
    val toe = 0.2f + 0.7f * r(3)
    val a = FloatArray(mw * mh); val streaks = FloatArray(mw * mh)
    for (y in 0 until mh) for (x in 0 until mw) {
        val u = x.toFloat() / mw; val v = y.toFloat() / mh
        val edge = 0.9f * (fbm(x / 10f, y / 10f, 13, 20, s, 3) - 0.5f)
        val oval = sq((u - ub) / 0.28f) + sq((v - vb) / 0.22f)
        val lump = sq((u - lumpU) / 0.2f) + sq((v - vb + 0.12f) / 0.14f)
        val ball = smooth(1.1f, 0.2f, min(oval, lump) + edge)
        val streak = (1.6f * fbm(x / 1.6f, y / 22f, 80, 9, s + 3, 3) - 0.45f).coerceIn(0f, 1f)
        val patch = (1.5f * fbm(x / 12f, y / 12f, 11, 16, s + 5, 3) - 0.2f).coerceIn(0f, 1f)
        var al = ball * (0.35f + 0.65f * streak) * (0.4f + 0.6f * patch)
        al = max(al, toe * smooth(1.1f, 0.2f, sq((u - 0.46f) / 0.24f) + sq((v - 0.15f) / 0.09f) + edge) * (0.3f + 0.7f * streak))
        a[y * mw + x] = al; streaks[y * mw + x] = streak
    }
    // смаз назад, к разбегу: нога чуть проскальзывает при постановке
    val len = (10 + 18 * r(4)).toInt()
    val out = FloatArray(mw * mh)
    for (y in 0 until mh) for (x in 0 until mw) {
        var m = a[y * mw + x]
        for (d in 1..min(len, y)) m = max(m, a[(y - d) * mw + x] * 0.6f * (1f - d.toFloat() / len) * streaks[y * mw + x])
        out[y * mw + x] = (m * 1.35f).coerceIn(0f, 1f)
    }
    SPIKES.forEachIndexed { i, (su, sv) ->
        if (r(10 + i) < 0.25f) return@forEachIndexed
        val cx = su * mw + 6 * (r(20 + i) - 0.5f); val cy = sv * mh + 6 * (r(30 + i) - 0.5f)
        val tail = 4 + 10 * r(40 + i)
        for (y in max(0, (cy - 4).toInt())..min(mh - 1, (cy + tail + 1).toInt())) for (x in max(0, (cx - 4).toInt())..min(mw - 1, (cx + 4).toInt())) {
            val c = hypot(x - cx, y - cy)
            var al = out[y * mw + x]
            if (c < 2.6f) al = max(al, 1f - c * 0.08f)
            val dy = y - cy
            if (abs(x - cx) < 1.2f && dy in 0f..tail) al = max(al, 0.8f * (1f - dy / tail))
            out[y * mw + x] = al
        }
    }
    return out
}

/** Набор текстур сцены; строится один раз в фоне (доли секунды) и живёт до конца процесса. */
class BoardTextures(
    val tartan: Bitmap, val board: Bitmap, val plasticine: Bitmap, val marks: List<Bitmap>, val dent: Bitmap, val ring: Bitmap
) {
    companion object {
        @Volatile private var cached: BoardTextures? = null
        val ready: BoardTextures? get() = cached

        fun get(): BoardTextures = cached ?: synchronized(this) { cached ?: build().also { cached = it } }

        private fun build(): BoardTextures {
            // тартан: полиуретан с вкраплениями крошки EPDM, рельеф освещён сбоку (плитка 512 px = 0,5 м, 1 px ≈ 1 мм)
            val n = 512
            val height = FloatArray(n * n)
            for (y in 0 until n) for (x in 0 until n) {
                val grain = hash(x / 2, y / 2, 3)                                          // крошка 2–3 мм
                height[y * n + x] = 0.55f * grain + 0.3f * fbm(x / 6f, y / 6f, 86, 86, 5, 2) + 0.15f * hash(x, y, 4)
            }
            val tartan = IntArray(n * n)
            for (y in 0 until n) for (x in 0 until n) {
                val h = height[y * n + x]
                val slope = h - height[y * n + (x + n - 1) % n] + 0.6f * (h - height[((y + n - 1) % n) * n + x])
                val light = 0.93f + 0.55f * slope
                val tone = 0.92f + 0.14f * fbm(x / 96f, y / 96f, 5, 5, 1, 3)               // неровность заливки
                val g = hash(x / 2, y / 2, 7)
                val k = light * tone * when {
                    g < 0.05f -> 0.62f                                                     // тёмная крошка
                    g > 0.975f -> 1.18f                                                    // светлая крошка
                    else -> 1f
                }
                tartan[y * n + x] = argb(1f, 0.64f * k, 0.22f * k, 0.16f * k)
            }
            // брусок: белая краска по дереву, волокна вдоль бруска, лёгкая затёртость и потемневшие кромки
            val bw = 1024; val bh = 168
            val board = IntArray(bw * bh)
            for (y in 0 until bh) for (x in 0 until bw) {
                val grain = 0.982f + 0.018f * sin(y * 0.55f + 9f * fbm(x / 160f, y / 18f, 7, 10, 31, 3))
                val wear = fbm(x / 70f, y / 30f, 15, 6, 33, 4)
                val dust = 0.04f * fbm(x / 12f, y / 12f, 86, 14, 37, 3)
                var k = grain * (0.965f - 0.05f * max(0f, wear - 0.6f) * 4f) - dust
                val edge = min(min(x, bw - 1 - x), min(y, bh - 1 - y))
                if (edge < 6) k *= 0.80f + 0.03f * edge
                board[y * bw + x] = argb(1f, 0.94f * k, 0.93f * k, 0.89f * k)
            }
            // пластилин: светло-серая масса с мягкими неровностями и следами выравнивания шпателем
            val pw = 1024; val ph = 86
            val plast = IntArray(pw * ph)
            for (y in 0 until ph) for (x in 0 until pw) {
                val lump = fbm(x / 50f, y / 20f, 21, 5, 41)
                val smear = fbm(x / 8f, y / 60f, 128, 2, 43, 2)
                val k = 0.84f + 0.12f * lump - 0.06f * smear
                plast[y * pw + x] = argb(1f, 0.82f * k, 0.82f * k, 0.78f * k)
            }
            // след шиповки: чёрная резина подошвы пятнами (протектор, протяжка при толчке) и проколы шипов (носок вверху)
            val mw = 128; val mh = 192
            val dent = IntArray(mw * mh)
            val marks = List(MARK_VARIANTS) { v -> bitmap(mw, mh, markAlpha(v, mw, mh).map { argb(it, 0.04f, 0.04f, 0.045f) }.toIntArray()) }
            for (y in 0 until mh) for (x in 0 until mw) {
                val inside = forefoot(x.toFloat(), y.toFloat(), mw, mh)
                // вмятина в пластилине: тень внутри и светлый валик выдавленной массы за носком
                val rim = forefoot(x.toFloat(), y.toFloat() + 9f, mw, mh) - inside
                dent[y * mw + x] = when {
                    inside > 0f -> argb(0.5f * inside, 0.30f, 0.30f, 0.29f)
                    rim > 0f && y < mh * 0.6f -> argb(0.7f * rim, 0.97f, 0.97f, 0.95f)
                    else -> 0
                }
            }
            // кольцо: выбранная попытка и попытка на старте
            val gs = 96
            val ring = IntArray(gs * gs)
            for (y in 0 until gs) for (x in 0 until gs) {
                val d = hypot(x - gs / 2f + 0.5f, y - gs / 2f + 0.5f) / (gs / 2f)
                ring[y * gs + x] = argb((1f - abs(d - 0.88f) / 0.06f).coerceIn(0f, 1f), 1f, 1f, 1f)
            }
            return BoardTextures(
                bitmap(n, n, tartan), bitmap(bw, bh, board), bitmap(pw, ph, plast),
                marks, bitmap(mw, mh, dent), bitmap(gs, gs, ring)
            )
        }
    }
}

// ------------------------------------------------------------------ камера

/** Камера, вращающаяся вокруг точки [target]: поворот [yaw], наклон [pitch] (градусы), расстояние [dist]. */
private class OrbitCamera(yaw: Float, pitch: Float, val dist: Float, val target: V3, val cx: Float, val cy: Float, val f: Float) {
    private val cyw = cos(yaw * PI / 180).toFloat(); private val syw = sin(yaw * PI / 180).toFloat()
    private val cp = cos(pitch * PI / 180).toFloat(); private val sp = sin(pitch * PI / 180).toFloat()

    fun view(p: V3): V3 {
        val x = p.x - target.x; val y = p.y - target.y; val z = p.z - target.z
        val x1 = x * cyw - z * syw; val z1 = x * syw + z * cyw
        return V3(x1, y * cp + z1 * sp, -y * sp + z1 * cp + dist)
    }

    fun project(p: V3): androidx.compose.ui.geometry.Offset? {
        val v = view(p)
        if (v.z < NEAR) return null
        return androidx.compose.ui.geometry.Offset(cx + v.x * f / v.z, cy - v.y * f / v.z)
    }
}

// ------------------------------------------------------------------ отрисовка

private class Painter(val nc: android.graphics.Canvas, val cam: OrbitCamera) {
    private val m = Matrix()
    private val path = android.graphics.Path()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** Проецирует четырёхугольник; false — часть за камерой. Края расширены на 0,6 px, чтобы не было щелей. */
    private fun quad(a: V3, b: V3, c: V3, d: V3, grow: Float = 0.6f): Boolean {
        val pts = listOf(a, b, c, d).map { cam.project(it) ?: return false }
        val mx = pts.sumOf { it.x.toDouble() }.toFloat() / 4; val my = pts.sumOf { it.y.toDouble() }.toFloat() / 4
        path.reset()
        pts.forEachIndexed { i, p ->
            dst[i * 2] = p.x; dst[i * 2 + 1] = p.y
            val l = max(1e-3f, hypot(p.x - mx, p.y - my))
            val ex = p.x + (p.x - mx) / l * grow; val ey = p.y + (p.y - my) / l * grow
            if (i == 0) path.moveTo(ex, ey) else path.lineTo(ex, ey)
        }
        path.close()
        return true
    }

    /**
     * Текстурированная горизонтальная плоскость y = [y] от (x0, z0) до (x1, z1), разбитая на nx × nz участков.
     * [u] и [v] переводят мировые координаты в пиксели текстуры.
     */
    fun plane(x0: Float, z0: Float, x1: Float, z1: Float, y: Float, shader: BitmapShader, nx: Int, nz: Int,
              u: (Float) -> Float, v: (Float) -> Float) {
        paint.shader = shader; paint.alpha = 255; paint.colorFilter = null
        for (i in 0 until nx) for (j in 0 until nz) {
            val ax = x0 + (x1 - x0) * i / nx; val bx = x0 + (x1 - x0) * (i + 1) / nx
            val az = z0 + (z1 - z0) * j / nz; val bz = z0 + (z1 - z0) * (j + 1) / nz
            if (!quad(V3(ax, y, az), V3(bx, y, az), V3(bx, y, bz), V3(ax, y, bz))) continue
            src[0] = u(ax); src[1] = v(az); src[2] = u(bx); src[3] = v(az); src[4] = u(bx); src[5] = v(bz); src[6] = u(ax); src[7] = v(bz)
            m.setPolyToPoly(src, 0, dst, 0, 4)
            shader.setLocalMatrix(m)
            nc.drawPath(path, paint)
        }
        paint.shader = null
    }

    /** Рисует [block] только внутри горизонтального прямоугольника — например, вмятину только на пластилине. */
    fun clipped(x0: Float, z0: Float, x1: Float, z1: Float, y: Float, block: () -> Unit) {
        if (!quad(V3(x0, y, z0), V3(x1, y, z0), V3(x1, y, z1), V3(x0, y, z1), 0f)) return
        val clip = android.graphics.Path(path)
        nc.save()
        nc.clipPath(clip)
        block()
        nc.restore()
    }

    fun flat(a: V3, b: V3, c: V3, d: V3, color: Int, grow: Float = 0.6f) {
        if (!quad(a, b, c, d, grow)) return
        paint.shader = null; paint.colorFilter = null; paint.color = color
        nc.drawPath(path, paint)
    }

    /** Горизонтальный прямоугольник цвета [color] на высоте [y]. */
    fun rect(x0: Float, z0: Float, x1: Float, z1: Float, y: Float, color: Int, grow: Float = 0.6f) =
        flat(V3(x0, y, z0), V3(x1, y, z0), V3(x1, y, z1), V3(x0, y, z1), color, grow)

    /**
     * Картинка [bmp] на поверхности: прямоугольник с центром (x, z), размером w × l метров, верх картинки — к +z,
     * повёрнутый на [angle] градусов вокруг вертикали.
     */
    fun decal(bmp: Bitmap, x: Float, z: Float, y: Float, w: Float, l: Float, angle: Float = 0f, tint: Int? = null, alpha: Int = 255) {
        val ca = cos(angle * PI / 180).toFloat(); val sa = sin(angle * PI / 180).toFloat()
        fun corner(dx: Float, dz: Float) = V3(x + dx * ca - dz * sa, y, z + dx * sa + dz * ca)
        if (!quad(corner(-w / 2, l / 2), corner(w / 2, l / 2), corner(w / 2, -l / 2), corner(-w / 2, -l / 2), 0f)) return
        src[0] = 0f; src[1] = 0f; src[2] = bmp.width.toFloat(); src[3] = 0f
        src[4] = bmp.width.toFloat(); src[5] = bmp.height.toFloat(); src[6] = 0f; src[7] = bmp.height.toFloat()
        m.setPolyToPoly(src, 0, dst, 0, 4)
        val sh = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { it.setLocalMatrix(m) }
        paint.shader = sh; paint.alpha = alpha
        paint.colorFilter = tint?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
        nc.drawPath(path, paint)
        paint.shader = null; paint.colorFilter = null; paint.alpha = 255
    }
}

/** Детерминированный разброс следа: одна и та же попытка всегда лежит одинаково. */
private fun jitter(s: RunUpShot, k: Int, salt: Int): Float = hash((s.date / 1000).toInt(), k * 31 + (s.feet * 100).toInt(), salt) - 0.5f

/** Цвет тепловой карты, как в футболе: синий — редко, зелёный, жёлтый, красный — чаще всего. */
fun heatColor(d: Float): Color {
    val stops = listOf(0f to Color(0xFF2E7DFF), 0.35f to Color(0xFF22C55E), 0.65f to Color(0xFFFACC15), 1f to Color(0xFFEF4444))
    val v = d.coerceIn(0f, 1f)
    val i = stops.indexOfLast { it.first <= v }.coerceIn(0, stops.size - 2)
    val (a, ca) = stops[i]
    val (b, cb) = stops[i + 1]
    return androidx.compose.ui.graphics.lerp(ca, cb, ((v - a) / (b - a)).coerceIn(0f, 1f))
}

/** Полос тепловой карты вдоль дорожки и ширина пятна одной попытки (0,25 стопы). */
private const val HEAT_STRIPS = 160
private const val HEAT_SIGMA = 0.25f * FOOT

/**
 * Где лежит носок поперёк дорожки. В попытке вводится только отклонение вдоль разбега, поперёк его никто не меряет,
 * поэтому след ставится туда, где обычно толкается спортсмен: на 10 см в сторону толчковой ноги из профиля
 * (без неё — по центру). Одинаковые отклонения — рядом через 7,5 см по очереди вправо и влево
 * ([stackIndex] — номер в столбике), плюс разброс до 1,5 см, чтобы следы не стояли строем.
 */
fun lateralX(takeOffLeg: String?, stackIndex: Int, jitter: Float): Float {
    val base = when (takeOffLeg) { "LEFT" -> -0.10f; "RIGHT" -> 0.10f; else -> 0f }
    val step = (stackIndex + 1) / 2 * if (stackIndex % 2 == 1) 1 else -1
    return (base + step * 0.075f + 0.03f * jitter).coerceIn(-BOARD_HALF + 0.06f, BOARD_HALF - 0.06f)
}

/**
 * 3D-вид попаданий на разбеге: кусок дорожки с бруском крупным планом, каждая попытка — чёрный след шиповки там,
 * где пришлась нога (вдоль дорожки — ровно по введённым стопам, поперёк — см. [lateralX]); свежие следы темнее
 * старых. [heat] — тепловая карта: полосы поперёк дорожки, чем чаще носок приходился на это место, тем краснее.
 * Палец вращает сцену, щипок приближает, двойное нажатие возвращает вид, нажатие на след выбирает попытку.
 */
@Composable
fun RunUpBoard3D(
    placed: List<Pair<RunUpShot, Int>>,
    median: Double,
    span: Double,
    picked: RunUpShot?,
    onPick: (RunUpShot?) -> Unit,
    start: Color,
    modifier: Modifier = Modifier,
    heat: Boolean = false,
    takeOffLeg: String? = null
) {
    val tex by produceState(BoardTextures.ready) { if (value == null) value = withContext(Dispatchers.Default) { BoardTextures.get() } }
    // кусок дорожки: от недоступа на span стоп до заступа на span стоп с запасом
    val zA = remember(span) { toeZ(-span) - MARK_L - 0.18f }
    val zB = remember(span) { toeZ(span) + 0.22f }
    val home = remember(span) { 0.6f + 0.9f * (zB - zA) }
    var yaw by remember { mutableFloatStateOf(-30f) }
    var pitch by remember { mutableFloatStateOf(38f) }
    var dist by remember(span) { mutableFloatStateOf(home) }
    val target = remember(span) { V3(0f, -0.03f, (zA + zB) / 2) }
    fun camera(size: Size) = OrbitCamera(yaw, pitch, dist, target, size.width / 2, size.height * 0.40f, size.width * 1.05f)
    fun spot(s: RunUpShot, k: Int) = V3(lateralX(takeOffLeg, k, jitter(s, k, 1)), 0f, toeZ(s.feet) - MARK_L / 2)
    val ordered = remember(placed) { placed.sortedBy { it.first.date } }
    // тепловая карта вдоль дорожки: сумма гауссовых пятен по месту носка, нормированная к самому частому месту
    val heatDensity = remember(placed, zA, zB) {
        val raw = FloatArray(HEAT_STRIPS) { i ->
            val z = zA + (zB - zA) * (i + 0.5f) / HEAT_STRIPS
            placed.sumOf { (s, _) -> kotlin.math.exp(-0.5 * ((z - toeZ(s.feet)) / HEAT_SIGMA).let { it * it }) }.toFloat()
        }
        val top = raw.maxOrNull()?.takeIf { it > 0f } ?: 1f
        FloatArray(HEAT_STRIPS) { raw[it] / top }
    }

    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        yaw = (yaw + pan.x * 0.25f).coerceIn(-85f, 85f)
                        pitch = (pitch + pan.y * 0.15f).coerceIn(8f, 85f)
                        dist = (dist / zoom).coerceIn(home * 0.35f, home * 2f)
                    }
                }
                .pointerInput(placed, yaw, pitch, dist, heat) {
                    detectTapGestures(onDoubleTap = { yaw = -30f; pitch = 38f; dist = home }) { tap ->
                        if (heat) return@detectTapGestures
                        val cam = camera(Size(size.width.toFloat(), size.height.toFloat()))
                        val best = placed.mapNotNull { (s, k) -> cam.project(spot(s, k))?.let { s to hypot(it.x - tap.x, it.y - tap.y) } }
                            .minByOrNull { it.second }
                        onPick(best?.takeIf { it.second < 36f * density }?.first)
                    }
                }
        ) {
            // фон: нейтральная студийная подложка
            drawRect(Brush.verticalGradient(listOf(Color(0xFF34373B), Color(0xFF1B1C1F))))
            val t = tex ?: return@Canvas
            val cam = camera(size)
            val mz = toeZ(median)
            drawIntoCanvas { canvas ->
                val p = Painter(canvas.nativeCanvas, cam)
                fun rep(b: Bitmap) = BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                fun clamp(b: Bitmap) = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val bottom = -TARTAN - BASE

                // мягкая тень куска дорожки на подложке
                for (i in 6 downTo 1) {
                    val g = 0.03f * i
                    p.rect(-SLAB_HALF - g, zA - g, SLAB_HALF + g, zB + g, bottom, 0x12000000, 0f)
                }
                // срез: тартан сверху, асфальтовое основание снизу; дальние стенки рисуются первыми
                data class Wall(val a: V3, val b: V3, val shade: Float)
                val walls = listOf(
                    Wall(V3(-SLAB_HALF, 0f, zA), V3(SLAB_HALF, 0f, zA), 0.78f),
                    Wall(V3(SLAB_HALF, 0f, zB), V3(-SLAB_HALF, 0f, zB), 0.55f),
                    Wall(V3(-SLAB_HALF, 0f, zB), V3(-SLAB_HALF, 0f, zA), 0.92f),
                    Wall(V3(SLAB_HALF, 0f, zA), V3(SLAB_HALF, 0f, zB), 0.62f)
                ).sortedByDescending { cam.view(V3((it.a.x + it.b.x) / 2, -0.04f, (it.a.z + it.b.z) / 2)).z }
                walls.forEach { w ->
                    fun c(r: Float, g: Float, b: Float) = argb(1f, r * w.shade, g * w.shade, b * w.shade)
                    p.flat(V3(w.a.x, 0f, w.a.z), V3(w.b.x, 0f, w.b.z), V3(w.b.x, -TARTAN, w.b.z), V3(w.a.x, -TARTAN, w.a.z), c(0.55f, 0.19f, 0.14f))
                    p.flat(V3(w.a.x, -TARTAN, w.a.z), V3(w.b.x, -TARTAN, w.b.z), V3(w.b.x, bottom, w.b.z), V3(w.a.x, bottom, w.a.z), c(0.30f, 0.30f, 0.29f))
                    // линия клея между тартаном и основанием
                    p.flat(V3(w.a.x, -TARTAN, w.a.z), V3(w.b.x, -TARTAN, w.b.z), V3(w.b.x, -TARTAN - 0.003f, w.b.z), V3(w.a.x, -TARTAN - 0.003f, w.a.z), c(0.12f, 0.11f, 0.10f))
                }

                // верх: тартан, разметка дорожки, брусок и пластилин
                p.plane(-SLAB_HALF, zA, SLAB_HALF, zB, 0f, rep(t.tartan), 4, 14, { it * 1024f }, { -it * 1024f })
                for (sx in listOf(-1f, 1f)) {
                    val a = BOARD_HALF * sx; val b = (BOARD_HALF + LINE) * sx
                    p.rect(min(a, b), zA, max(a, b), zB, 0.0005f, 0xF0EDEDE8.toInt())
                }
                // зазор вокруг бруска и мягкая тень на тартане
                p.rect(-BOARD_HALF, -0.012f, BOARD_HALF, 0.312f, 0.0008f, 0x30000000, 0f)
                p.rect(-BOARD_HALF, -0.004f, BOARD_HALF, 0.304f, 0.0009f, 0xC0181818.toInt(), 0f)
                p.plane(-BOARD_HALF + 0.003f, 0f, BOARD_HALF - 0.003f, 0.20f, 0.001f, clamp(t.board), 4, 1,
                    { (it + BOARD_HALF) / (2 * BOARD_HALF) * t.board.width }, { (0.20f - it) / 0.20f * t.board.height })
                // пластилин: скос 45° к бруску и поднятая на 7 мм полка
                p.flat(
                    V3(-BOARD_HALF + 0.003f, 0.001f, 0.200f), V3(BOARD_HALF - 0.003f, 0.001f, 0.200f),
                    V3(BOARD_HALF - 0.003f, PLAST_Y, 0.207f), V3(-BOARD_HALF + 0.003f, PLAST_Y, 0.207f), 0xFFB4B4AD.toInt()
                )
                p.plane(-BOARD_HALF + 0.003f, 0.207f, BOARD_HALF - 0.003f, 0.297f, PLAST_Y, clamp(t.plasticine), 4, 1,
                    { (it + BOARD_HALF) / (2 * BOARD_HALF) * t.plasticine.width }, { (it - 0.207f) / 0.09f * t.plasticine.height })
                // меловые отметки через стопу вдоль правой линии и типичное отклонение (медиана) — пунктир поперёк
                var f = -span
                while (f <= span + 1e-9) {
                    val z = toeZ(f)
                    if (z in zA..zB) p.rect(BOARD_HALF + LINE + 0.01f, z - 0.004f, BOARD_HALF + LINE + 0.09f, z + 0.004f, 0.0006f, 0xE6F4F4F0.toInt())
                    f += 1.0
                }
                // на белом бруске меловой пунктир не виден, а медиана там и так означает «попал»
                if (mz in zA..zB && mz !in 0f..0.2f) {
                    var x = -BOARD_HALF
                    val y = if (mz in 0.2f..0.3f) PLAST_Y + 0.0005f else 0.0015f
                    while (x < BOARD_HALF) {
                        p.rect(x, mz - 0.004f, min(x + 0.06f, BOARD_HALF), mz + 0.004f, y, 0xB4FFFFFF.toInt())
                        x += 0.11f
                    }
                }

                // тепловая карта: полосы поперёк всей дорожки — поперёк место попадания не измеряется
                if (heat) for (i in 0 until HEAT_STRIPS) {
                    val d = heatDensity[i]
                    if (d < 0.06f) continue
                    val z0 = zA + (zB - zA) * i / HEAT_STRIPS; val z1 = zA + (zB - zA) * (i + 1) / HEAT_STRIPS
                    val y = if (z0 >= 0.2f && z1 <= 0.3f) PLAST_Y + 0.0008f else 0.0018f
                    p.rect(-BOARD_HALF, z0, BOARD_HALF, z1, y, heatColor(d).copy(alpha = 0.2f + 0.6f * d).toArgb(), 0.4f)
                }
                // следы попыток: старые бледнее, свежие темнее; на пластилине — вмятина
                if (!heat) ordered.forEachIndexed { i, (s, k) ->
                    val c = spot(s, k)
                    val angle = 7f * jitter(s, k, 2) * 2f
                    val onPlast = c.z + MARK_L / 2 > 0.2f && c.z - MARK_L / 2 < 0.3f
                    val y = if (onPlast) PLAST_Y + 0.0008f else 0.0018f
                    val age = if (ordered.size <= 1) 1f else i.toFloat() / (ordered.size - 1)
                    val alpha = (150 + 100 * age).toInt()
                    if (onPlast) p.clipped(-BOARD_HALF, 0.200f, BOARD_HALF, 0.297f, PLAST_Y) {
                        p.decal(t.dent, c.x, c.z, y, MARK_W, MARK_L, angle, null, 235)
                    }
                    val mark = t.marks[((jitter(s, k, 3) + 0.5f) * MARK_VARIANTS).toInt().coerceIn(0, MARK_VARIANTS - 1)]
                    p.decal(mark, c.x, c.z, y + 0.0003f, MARK_W, MARK_L, angle, null, if (onPlast) alpha * 2 / 3 else alpha)
                    if (s.competition) p.decal(t.ring, c.x, c.z, y + 0.0006f, 0.17f, 0.22f, angle, start.toArgb(), 210)
                    if (s == picked) p.decal(t.ring, c.x, c.z, y + 0.0009f, 0.20f, 0.26f, angle, android.graphics.Color.WHITE, 255)
                }
            }
            // свет сверху-слева и мягкая виньетка
            drawRect(Brush.radialGradient(listOf(Color(0x1AFFF4E0), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height * 0.2f), radius = size.maxDimension * 0.8f))
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)), center = center, radius = size.maxDimension * 0.75f))
            // подписи: брусок, пластилин и отметки в стопах вдоль дорожки
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE; textSize = 11.5f * density
                    setShadowLayer(4f, 0f, 1f, 0xCC000000.toInt())
                }
                fun label(s: String, at: V3, align: Paint.Align = Paint.Align.CENTER) {
                    cam.project(at)?.let { text.textAlign = align; nc.drawText(s, it.x, it.y, text) }
                }
                label("брусок", V3(-SLAB_HALF - 0.04f, 0f, 0.10f), Paint.Align.RIGHT)
                label("пластилин", V3(-SLAB_HALF - 0.04f, 0f, 0.27f), Paint.Align.RIGHT)
                var f = -span
                while (f <= span + 1e-9) {
                    if (f != 0.0 && toeZ(f) in zA..zB) {
                        label((if (f > 0) "+" else "−") + formatNumber(abs(f)), V3(SLAB_HALF + 0.04f, 0f, toeZ(f)), Paint.Align.LEFT)
                    }
                    f += 1.0
                }
                label("медиана", V3(SLAB_HALF + 0.04f, 0f, mz - 0.03f), Paint.Align.LEFT)
            }
        }
        if (tex == null) {
            Text("Готовим сцену…", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center))
        }
    }
}
