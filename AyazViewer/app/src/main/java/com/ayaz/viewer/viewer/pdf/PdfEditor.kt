package com.ayaz.viewer.viewer.pdf

import android.content.Context
import androidx.compose.ui.geometry.Offset
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import java.io.File
import java.io.IOException
import java.util.Calendar

/** Sayfa üzerine eklenen işaretleme. Koordinatlar ekranda görünen sayfaya göredir (nokta, sol üst orijin). */
sealed interface PdfMark {
    val page: Int
}

data class InkMark(
    override val page: Int,
    val points: List<Offset>,
    val color: Int,
    /** Çizgi kalınlığı (nokta). */
    val width: Float,
    val highlighter: Boolean,
) : PdfMark

data class TextMark(
    override val page: Int,
    /** Metin kutusunun sol üst köşesi (nokta). */
    val x: Float,
    val y: Float,
    val text: String,
    val color: Int,
    val size: Float,
) : PdfMark

/** Sayfa işlemleri: döndürme (derece, 90'ın katı) ve silme. */
sealed interface PageOp {
    data class Rotate(val page: Int, val degrees: Int) : PageOp
    data class Delete(val page: Int) : PageOp
}

/**
 * PDFBox ile işaretlemeleri gerçek PDF notları (Ink / FreeText) olarak yazar; görünüm akışı
 * (appearance stream) eklendiği için tüm görüntüleyicilerde aynı görünür. Belgenin kendi metni
 * değiştirilmez; aranabilir ve seçilebilir kalır.
 */
object PdfEditor {

    private const val AUTHOR = "Ayaz Viewer"

    @Throws(IOException::class)
    fun write(context: Context, source: File, target: File, marks: List<PdfMark>, op: PageOp? = null) {
        PDDocument.load(source, MemoryUsageSetting.setupMixed(48L * 1024 * 1024)).use { doc ->
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            var font: PDFont? = null
            for (mark in marks) {
                if (mark.page !in 0 until doc.numberOfPages) continue
                val page = doc.getPage(mark.page)
                val annot = when (mark) {
                    is InkMark -> inkAnnotation(doc, page, mark)
                    is TextMark -> {
                        val f = font ?: context.assets.open("fonts/DejaVuSans.ttf").use { PDType0Font.load(doc, it) }
                            .also { font = it }
                        textAnnotation(doc, page, mark, f)
                    }
                } ?: continue
                val list = page.annotations.toMutableList()
                list.add(annot)
                page.annotations = list
            }
            when (op) {
                is PageOp.Rotate -> if (op.page in 0 until doc.numberOfPages) {
                    val p = doc.getPage(op.page)
                    p.rotation = (((p.rotation + op.degrees) % 360) + 360) % 360
                }
                is PageOp.Delete -> if (op.page in 0 until doc.numberOfPages && doc.numberOfPages > 1) {
                    doc.removePage(op.page)
                }
                null -> {}
            }
            val tmp = File(target.parentFile, target.name + ".tmp")
            doc.save(tmp)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    // ------------------------------------------------------------------ koordinatlar

    /**
     * Ekran (döndürülmüş, y aşağı) koordinatlarını, y'si yukarı bakan "görünüm" uzayına taşıyan
     * ve oradan PDF kullanıcı uzayına geçen dönüşüm. Döndürme ve CropBox hesaba katılır.
     */
    private class Space(page: PDPage) {
        val crop: PDRectangle = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360
        val displayHeight: Float = if (rotation == 90 || rotation == 270) crop.width else crop.height
        private val llx = crop.lowerLeftX
        private val lly = crop.lowerLeftY
        private val urx = crop.upperRightX
        private val ury = crop.upperRightY

        /** a b c d e f: (x, y') → (a x + c y' + e, b x + d y' + f) */
        val m: FloatArray = when (rotation) {
            90 -> floatArrayOf(0f, 1f, -1f, 0f, urx, lly)
            180 -> floatArrayOf(-1f, 0f, 0f, -1f, urx, ury)
            270 -> floatArrayOf(0f, -1f, 1f, 0f, llx, ury)
            else -> floatArrayOf(1f, 0f, 0f, 1f, llx, lly)
        }

        /** Ekran y'sini görünüm y'sine çevirir. */
        fun flipY(y: Float) = displayHeight - y

        fun toUser(x: Float, yUp: Float): Pair<Float, Float> =
            (m[0] * x + m[2] * yUp + m[4]) to (m[1] * x + m[3] * yUp + m[5])

        /** Görünüm uzayındaki kutunun kullanıcı uzayındaki sınırlayıcı dikdörtgeni. */
        fun userRect(x0: Float, y0: Float, x1: Float, y1: Float): PDRectangle {
            val pts = listOf(toUser(x0, y0), toUser(x1, y0), toUser(x0, y1), toUser(x1, y1))
            val minX = pts.minOf { it.first }
            val minY = pts.minOf { it.second }
            val maxX = pts.maxOf { it.first }
            val maxY = pts.maxOf { it.second }
            return PDRectangle(minX, minY, maxX - minX, maxY - minY)
        }

        fun matrixArray(): COSArray = COSArray().apply { m.forEach { add(COSFloat(it)) } }
    }

    private fun rgb(color: Int): FloatArray = floatArrayOf(
        ((color shr 16) and 0xFF) / 255f, ((color shr 8) and 0xFF) / 255f, (color and 0xFF) / 255f,
    )

    private fun newAppearance(doc: PDDocument, space: Space, x0: Float, y0: Float, x1: Float, y1: Float): PDAppearanceStream {
        val ap = PDAppearanceStream(doc)
        ap.bBox = PDRectangle(x0, y0, x1 - x0, y1 - y0)
        ap.resources = PDResources()
        ap.cosObject.setItem(COSName.MATRIX, space.matrixArray())
        return ap
    }

    private fun common(annot: PDAnnotationMarkup, color: Int) {
        annot.color = PDColor(rgb(color), PDDeviceRGB.INSTANCE)
        annot.titlePopup = AUTHOR
        annot.setPrinted(true)
        annot.creationDate = Calendar.getInstance()
        annot.setModifiedDate(Calendar.getInstance())
    }

    private fun inkAnnotation(doc: PDDocument, page: PDPage, mark: InkMark): PDAnnotation? {
        if (mark.points.isEmpty()) return null
        val space = Space(page)
        val pts = mark.points.map { Offset(it.x, space.flipY(it.y)) }
        val pad = mark.width
        val x0 = pts.minOf { it.x } - pad
        val y0 = pts.minOf { it.y } - pad
        val x1 = pts.maxOf { it.x } + pad
        val y1 = pts.maxOf { it.y } + pad
        val alpha = if (mark.highlighter) 0.4f else 1f

        val ap = newAppearance(doc, space, x0, y0, x1, y1)
        PDPageContentStream(doc, ap).use { cs ->
            val gs = PDExtendedGraphicsState()
            gs.strokingAlphaConstant = alpha
            if (mark.highlighter) gs.blendMode = BlendMode.MULTIPLY
            cs.setGraphicsStateParameters(gs)
            val c = rgb(mark.color)
            cs.setStrokingColor(c[0], c[1], c[2])
            cs.setLineWidth(mark.width)
            cs.setLineCapStyle(if (mark.highlighter) 2 else 1)
            cs.setLineJoinStyle(1)
            cs.moveTo(pts[0].x, pts[0].y)
            if (pts.size == 1) cs.lineTo(pts[0].x + 0.01f, pts[0].y) else for (i in 1 until pts.size) cs.lineTo(pts[i].x, pts[i].y)
            cs.stroke()
        }

        val annot = PDAnnotationMarkup()
        annot.cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK)
        common(annot, mark.color)
        annot.constantOpacity = alpha
        annot.borderStyle = PDBorderStyleDictionary().apply { width = mark.width }
        annot.rectangle = space.userRect(x0, y0, x1, y1)
        val path = COSArray()
        for (p in pts) {
            val (ux, uy) = space.toUser(p.x, p.y)
            path.add(COSFloat(ux)); path.add(COSFloat(uy))
        }
        annot.cosObject.setItem(COSName.getPDFName("InkList"), COSArray().apply { add(path) })
        annot.contents = if (mark.highlighter) "Vurgu" else "Çizim"
        annot.appearance = PDAppearanceDictionary().apply { setNormalAppearance(ap) }
        return annot
    }

    private fun textAnnotation(doc: PDDocument, page: PDPage, mark: TextMark, font: PDFont): PDAnnotation? {
        val lines = mark.text.replace("\r", "").split('\n')
        if (lines.all { it.isBlank() }) return null
        val space = Space(page)
        val leading = mark.size * 1.25f
        val pad = mark.size * 0.3f
        val widest = lines.maxOf { safeWidth(font, it) / 1000f * mark.size }
        val x0 = mark.x - pad
        val yTop = space.flipY(mark.y) + pad
        val x1 = mark.x + widest + pad
        val y0 = yTop - pad * 2 - leading * lines.size
        val ap = newAppearance(doc, space, x0, y0, x1, yTop)
        PDPageContentStream(doc, ap).use { cs ->
            val c = rgb(mark.color)
            cs.beginText()
            cs.setFont(font, mark.size)
            cs.setNonStrokingColor(c[0], c[1], c[2])
            var y = yTop - pad - mark.size
            var first = true
            for (line in lines) {
                if (first) { cs.newLineAtOffset(mark.x, y); first = false } else cs.newLineAtOffset(0f, -leading)
                cs.showText(supported(font, line))
                y -= leading
            }
            cs.endText()
        }
        val annot = PDAnnotationMarkup()
        annot.cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_FREETEXT)
        common(annot, mark.color)
        annot.cosObject.setString(COSName.DA, "/Helv ${mark.size} Tf 0 g")
        annot.borderStyle = PDBorderStyleDictionary().apply { width = 0f }
        annot.rectangle = space.userRect(x0, y0, x1, yTop)
        annot.contents = mark.text
        annot.appearance = PDAppearanceDictionary().apply { setNormalAppearance(ap) }
        return annot
    }

    private fun safeWidth(font: PDFont, s: String): Float = try {
        font.getStringWidth(supported(font, s))
    } catch (_: Exception) {
        s.length * 500f
    }

    /** Yazı tipinde bulunmayan karakterleri (ör. emoji) "?" ile değiştirir; aksi halde PDFBox hata verir. */
    private fun supported(font: PDFont, s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val ch = String(Character.toChars(cp))
            val ok = try { font.getStringWidth(ch); true } catch (_: Exception) { false }
            sb.append(if (ok) ch else "?")
            i += Character.charCount(cp)
        }
        return sb.toString()
    }
}
