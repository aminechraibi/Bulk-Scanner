package com.example.data

import android.content.Context
import android.graphics.*
import java.io.File
import java.io.FileOutputStream

object SampleDocumentGenerator {

    data class SampleDocType(
        val id: String,
        val displayName: String,
        val description: String,
        val baseColor: Int,
        val lineStyle: String // Grid, Ruled, Blank, Invoice
    )

    val sampleTypes = listOf(
        SampleDocType("math_exam", "Algebra Exam Page 1", "Handwritten equations & grades", Color.WHITE, "Ruled"),
        SampleDocType("written_notes", "Handwritten Essay", "Journal page with blue pen", Color.parseColor("#FFFDF0"), "Ruled"),
        SampleDocType("office_invoice", "Office Invoice", "Printed table with black text", Color.WHITE, "Invoice"),
        SampleDocType("sketchbook", "Creative Layout Sketch", "Pencil sketches and geometric shapes", Color.parseColor("#FAF6EE"), "Blank"),
        SampleDocType("grid_chart", "Physics Lab Graph", "Data points and coordinate grids", Color.parseColor("#F4FFF6"), "Grid")
    )

    fun generateSampleDocFile(context: Context, docId: String): File {
        val cacheDir = context.cacheDir
        val file = File(cacheDir, "${docId}_sample.jpg")
        
        // Let's create a high-resolution sheet of paper (e.g., 1200 x 1600 px)
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Find the template settings
        val docType = sampleTypes.firstOrNull { it.id == docId } ?: sampleTypes[0]

        // 1. Draw desk background (dark wood/slate color to make document pop!)
        canvas.drawColor(Color.parseColor("#121212"))

        // 2. Draw a slight background glow
        val radialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width / 2f, height / 2f, width.toFloat(),
                Color.parseColor("#2C2C2C"), Color.parseColor("#121212"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), radialPaint)

        // 3. Draw paper sheet (centered with slightly slanted/rotated perspective, or we draw it straight but with shadows)
        // To make it look like a physical paper on a dark table:
        val paperLeft = 120f
        val paperTop = 160f
        val paperRight = width - 120f
        val paperBottom = height - 160f
        val paperRect = RectF(paperLeft, paperTop, paperRight, paperBottom)

        // Draw soft drop shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0)
            maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.OUTER)
        }
        canvas.drawRect(paperRect, shadowPaint)

        // Draw actual paper base
        paint.color = docType.baseColor
        canvas.drawRect(paperRect, paint)

        // Draw subtle paper texture/border
        paint.color = Color.parseColor("#E0E0E0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(paperRect, paint)

        // 4. Draw paper lines/grids
        paint.style = Paint.Style.FILL
        if (docType.lineStyle == "Ruled") {
            // Draw margin line (red)
            paint.color = Color.parseColor("#FFC0C0")
            paint.strokeWidth = 3f
            canvas.drawLine(paperLeft + 150f, paperTop, paperLeft + 150f, paperBottom, paint)

            // Draw horizontal rules (blue)
            paint.color = Color.parseColor("#D0E0FF")
            paint.strokeWidth = 1.5f
            var y = paperTop + 100f
            while (y < paperBottom - 50f) {
                canvas.drawLine(paperLeft, y, paperRight, y, paint)
                y += 60f
            }
        } else if (docType.lineStyle == "Grid") {
            // Draw grid meshes
            paint.color = Color.parseColor("#E4F4EC")
            paint.strokeWidth = 1f
            var x = paperLeft + 50f
            while (x < paperRight) {
                canvas.drawLine(x, paperTop, x, paperBottom, paint)
                x += 40f
            }
            var y = paperTop + 50f
            while (y < paperBottom) {
                canvas.drawLine(paperLeft, y, paperRight, y, paint)
                y += 40f
            }
        }

        // 5. Draw document content (text, tables, handwriting, checkmarks)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#262626")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val handwritingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F3C8A") // Blue ink
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
        }

        val redInkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D32F2F") // Red grading ink
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }

        when (docType.id) {
            "math_exam" -> {
                // Title
                textPaint.textSize = 36f
                canvas.drawText("ALGEBRA 101 — MIDTERM EXAM", paperLeft + 180f, paperTop + 120f, textPaint)

                textPaint.textSize = 24f
                textPaint.typeface = Typeface.DEFAULT
                canvas.drawText("Name: John Doe", paperLeft + 180f, paperTop + 180f, textPaint)
                canvas.drawText("Date: June 28, 2026", paperLeft + 550f, paperTop + 180f, textPaint)

                // Exam Questions & John's Answers
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Q1: Solve for x:  3x + 12 = 27", paperLeft + 180f, paperTop + 300f, textPaint)

                // Handwritten answer
                canvas.drawText("3x = 15", paperLeft + 220f, paperTop + 360f, handwritingPaint)
                canvas.drawText("x = 5", paperLeft + 220f, paperTop + 420f, handwritingPaint)
                // Red grading checkmark
                canvas.drawLine(paperLeft + 450f, paperTop + 390f, paperLeft + 470f, paperTop + 420f, redInkPaint)
                canvas.drawLine(paperLeft + 470f, paperTop + 420f, paperLeft + 520f, paperTop + 350f, redInkPaint)

                // Q2
                canvas.drawText("Q2: Find the derivative of f(x) = x³ - 5x + 2", paperLeft + 180f, paperTop + 540f, textPaint)
                canvas.drawText("f'(x) = 3x² - 5", paperLeft + 220f, paperTop + 600f, handwritingPaint)
                // Red grading checkmark
                canvas.drawLine(paperLeft + 550f, paperTop + 570f, paperLeft + 570f, paperTop + 600f, redInkPaint)
                canvas.drawLine(paperLeft + 570f, paperTop + 600f, paperLeft + 620f, paperTop + 530f, redInkPaint)

                // Q3 (wrong answer)
                canvas.drawText("Q3: Integrate  ∫ 2x dx", paperLeft + 180f, paperTop + 720f, textPaint)
                canvas.drawText("= 2x² + C", paperLeft + 220f, paperTop + 780f, handwritingPaint) // Wrong! Should be x^2 + C
                // Red X
                canvas.drawLine(paperLeft + 460f, paperTop + 740f, paperLeft + 500f, paperTop + 780f, redInkPaint)
                canvas.drawLine(paperLeft + 500f, paperTop + 740f, paperLeft + 460f, paperTop + 780f, redInkPaint)
                canvas.drawText("= x² + C", paperLeft + 530f, paperTop + 780f, redInkPaint)

                // Grade bubble at the top right
                redInkPaint.style = Paint.Style.STROKE
                redInkPaint.strokeWidth = 6f
                canvas.drawCircle(paperRight - 120f, paperTop + 130f, 65f, redInkPaint)
                
                redInkPaint.style = Paint.Style.FILL
                redInkPaint.textSize = 48f
                canvas.drawText("A-", paperRight - 150f, paperTop + 148f, redInkPaint)
            }
            "written_notes" -> {
                textPaint.textSize = 34f
                canvas.drawText("Reflections on Deep Learning", paperLeft + 180f, paperTop + 120f, textPaint)

                handwritingPaint.textSize = 28f
                val lines = listOf(
                    "Today I explored the core differences between structured",
                    "learning and heuristic optimization models. Machine learning",
                    "algorithms often feel like black-boxes, but when we look at the",
                    "underlying linear algebra, it becomes beautiful vector projections.",
                    "The gradient descent algorithm iteratively optimizes parameter weights",
                    "to minimize the empirical risk function. What's fascinating is how",
                    "over-parameterized neural networks generalize so well despite",
                    "traditional statistical learning theory suggesting they should overfit.",
                    "This is sometimes called the 'double descent' phenomenon, where",
                    "beyond the interpolation threshold, test error begins dropping again.",
                    "Perhaps the implicit regularization of SGD is what biases the search",
                    "towards flatter, more generalizable local minima in the loss landscape."
                )
                
                var yOffset = paperTop + 240f
                for (line in lines) {
                    canvas.drawText(line, paperLeft + 180f, yOffset, handwritingPaint)
                    yOffset += 60f
                }
            }
            "office_invoice" -> {
                // Professional Invoice Heading
                textPaint.textSize = 42f
                canvas.drawText("GLOBAL TECH SOLUTIONS", paperLeft + 80f, paperTop + 100f, textPaint)
                
                textPaint.textSize = 22f
                textPaint.typeface = Typeface.DEFAULT
                canvas.drawText("128 Innovation Way, Suite 400", paperLeft + 80f, paperTop + 140f, textPaint)
                canvas.drawText("Silicon Valley, CA 94025", paperLeft + 80f, paperTop + 170f, textPaint)
                canvas.drawText("contact@globaltech.com", paperLeft + 80f, paperTop + 200f, textPaint)

                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textPaint.textSize = 32f
                canvas.drawText("INVOICE", paperRight - 220f, paperTop + 100f, textPaint)
                
                textPaint.typeface = Typeface.DEFAULT
                textPaint.textSize = 20f
                canvas.drawText("Invoice #: INV-2026-089", paperRight - 250f, paperTop + 140f, textPaint)
                canvas.drawText("Date: June 28, 2026", paperRight - 250f, paperTop + 170f, textPaint)
                canvas.drawText("Due Date: July 28, 2026", paperRight - 250f, paperTop + 200f, textPaint)

                // Divider line
                paint.color = Color.parseColor("#333333")
                paint.strokeWidth = 3f
                canvas.drawLine(paperLeft + 60f, paperTop + 260f, paperRight - 60f, paperTop + 260f, paint)

                // Bill to
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("BILLED TO:", paperLeft + 80f, paperTop + 310f, textPaint)
                textPaint.typeface = Typeface.DEFAULT
                canvas.drawText("Acme Corporation Inc.", paperLeft + 80f, paperTop + 345f, textPaint)
                canvas.drawText("Attn: Accounts Payable", paperLeft + 80f, paperTop + 375f, textPaint)
                canvas.drawText("456 Industrial Parkway, Austin TX", paperLeft + 80f, paperTop + 405f, textPaint)

                // Table Header
                var tableY = paperTop + 480f
                paint.color = Color.parseColor("#F0F0F0")
                paint.style = Paint.Style.FILL
                canvas.drawRect(paperLeft + 60f, tableY, paperRight - 60f, tableY + 50f, paint)

                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("DESCRIPTION", paperLeft + 80f, tableY + 34f, textPaint)
                canvas.drawText("QTY", paperLeft + 540f, tableY + 34f, textPaint)
                canvas.drawText("UNIT PRICE", paperLeft + 660f, tableY + 34f, textPaint)
                canvas.drawText("AMOUNT", paperRight - 180f, tableY + 34f, textPaint)

                // Table Items
                val items = listOf(
                    Triple("Enterprise Software License (Annual)", "1", "$2,450.00"),
                    Triple("Cloud Infrastructure Setup Support", "12 hrs", "$150.00"),
                    Triple("Premium Developer Consulting Service", "8 hrs", "$180.00"),
                    Triple("Monthly Security Assessment & Audit", "1", "$800.00")
                )

                textPaint.typeface = Typeface.DEFAULT
                var itemY = tableY + 100f
                paint.color = Color.parseColor("#E0E0E0")
                paint.strokeWidth = 1f
                for (item in items) {
                    canvas.drawText(item.first, paperLeft + 80f, itemY, textPaint)
                    canvas.drawText(item.second, paperLeft + 550f, itemY, textPaint)
                    
                    // Format columns
                    val qtyVal = if (item.second.contains("hrs")) item.second.split(" ")[0].toInt() else 1
                    val priceStr = item.third
                    val priceVal = priceStr.replace("$", "").replace(",", "").toDouble()
                    val totalVal = qtyVal * priceVal
                    val totalStr = "$" + String.format("%,.2f", totalVal)

                    canvas.drawText(priceStr, paperLeft + 660f, itemY, textPaint)
                    canvas.drawText(totalStr, paperRight - 180f, itemY, textPaint)

                    canvas.drawLine(paperLeft + 60f, itemY + 25f, paperRight - 60f, itemY + 25f, paint)
                    itemY += 75f
                }

                // Total calculation
                val totalY = itemY + 30f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Subtotal:", paperRight - 340f, totalY, textPaint)
                canvas.drawText("$6,490.00", paperRight - 180f, totalY, textPaint)

                canvas.drawText("Tax (0.0%):", paperRight - 340f, totalY + 45f, textPaint)
                canvas.drawText("$0.00", paperRight - 180f, totalY + 45f, textPaint)

                paint.color = Color.parseColor("#333333")
                paint.strokeWidth = 2f
                canvas.drawLine(paperRight - 340f, totalY + 65f, paperRight - 60f, totalY + 65f, paint)

                textPaint.textSize = 26f
                canvas.drawText("Total Due:", paperRight - 340f, totalY + 110f, textPaint)
                canvas.drawText("$6,490.00", paperRight - 180f, totalY + 110f, textPaint)
            }
            "sketchbook" -> {
                textPaint.textSize = 32f
                canvas.drawText("Ideation: Ergonomic Scanner Grip", paperLeft + 80f, paperTop + 100f, textPaint)

                // Draw some hand drawn curves and circles
                handwritingPaint.color = Color.parseColor("#555555") // Pencil gray
                handwritingPaint.strokeWidth = 3f
                handwritingPaint.style = Paint.Style.STROKE

                // Main handle arc
                canvas.drawArc(paperLeft + 200f, paperTop + 250f, paperLeft + 500f, paperTop + 850f, 0f, 360f, false, handwritingPaint)
                canvas.drawArc(paperLeft + 220f, paperTop + 270f, paperLeft + 480f, paperTop + 830f, 15f, 160f, false, handwritingPaint)

                // Shade lines
                var sy = paperTop + 450f
                while (sy < paperTop + 650f) {
                    canvas.drawLine(paperLeft + 250f, sy, paperLeft + 350f, sy - 40f, handwritingPaint)
                    sy += 15f
                }

                // Mechanical labels
                handwritingPaint.style = Paint.Style.FILL
                handwritingPaint.textSize = 24f
                canvas.drawText("Soft rubber grip", paperLeft + 550f, paperTop + 350f, handwritingPaint)
                canvas.drawLine(paperLeft + 380f, paperTop + 380f, paperLeft + 540f, paperTop + 350f, handwritingPaint)

                canvas.drawText("Dual capture triggers", paperLeft + 550f, paperTop + 480f, handwritingPaint)
                canvas.drawLine(paperLeft + 460f, paperTop + 490f, paperLeft + 540f, paperTop + 480f, handwritingPaint)

                canvas.drawText("Recessed lens shroud", paperLeft + 550f, paperTop + 620f, handwritingPaint)
                canvas.drawLine(paperLeft + 350f, paperTop + 660f, paperLeft + 540f, paperTop + 620f, handwritingPaint)
            }
            "grid_chart" -> {
                textPaint.textSize = 34f
                canvas.drawText("Fig 3.2: Voltage vs Resonant Frequency", paperLeft + 100f, paperTop + 100f, textPaint)

                // Draw chart axes
                handwritingPaint.color = Color.parseColor("#1B5E20") // Dark green marker
                handwritingPaint.strokeWidth = 4f
                val axisX0 = paperLeft + 150f
                val axisY0 = paperBottom - 150f
                val axisX1 = paperRight - 100f
                val axisY1 = paperTop + 180f

                canvas.drawLine(axisX0, axisY0, axisX1, axisY0, handwritingPaint) // X Axis
                canvas.drawLine(axisX0, axisY0, axisX0, axisY1, handwritingPaint) // Y Axis

                // Axis ticks & labels
                textPaint.textSize = 18f
                textPaint.typeface = Typeface.DEFAULT
                for (i in 0..5) {
                    val tx = axisX0 + i * (axisX1 - axisX0) / 5f
                    canvas.drawLine(tx, axisY0, tx, axisY0 + 15f, handwritingPaint)
                    canvas.drawText("${i * 2}V", tx - 20f, axisY0 + 45f, textPaint)

                    val ty = axisY0 - i * (axisY0 - axisY1) / 5f
                    canvas.drawLine(axisX0 - 15f, ty, axisX0, ty, handwritingPaint)
                    canvas.drawText("${i * 10}kHz", axisX0 - 80f, ty + 8f, textPaint)
                }

                // Plot a resonant curve
                handwritingPaint.color = Color.parseColor("#C62828") // Red plot ink
                handwritingPaint.style = Paint.Style.STROKE
                val path = Path()
                
                // Gaussian resonance curve calculation
                var first = true
                for (x in axisX0.toInt()..axisX1.toInt() step 5) {
                    val px = (x - axisX0) / (axisX1 - axisX0) // normalized 0 to 1
                    // Resonant peak around px = 0.55
                    val power = Math.exp(-Math.pow((px - 0.55) / 0.18, 2.0))
                    val py = axisY0 - power * (axisY0 - axisY1) * 0.85f
                    
                    if (first) {
                        path.moveTo(x.toFloat(), py.toFloat())
                        first = false
                    } else {
                        path.lineTo(x.toFloat(), py.toFloat())
                    }
                }
                canvas.drawPath(path, handwritingPaint)

                // Label peak
                handwritingPaint.style = Paint.Style.FILL
                handwritingPaint.textSize = 24f
                canvas.drawCircle(axisX0 + 0.55f * (axisX1 - axisX0), axisY0 - 0.85f * (axisY0 - axisY1), 8f, handwritingPaint)
                canvas.drawText("Resonant peak fc = 11.2 kHz", axisX0 + 0.45f * (axisX1 - axisX0) + 10f, axisY0 - 0.85f * (axisY0 - axisY1) - 30f, handwritingPaint)
            }
        }

        // Save generated bitmap to the file
        val outStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)
        outStream.flush()
        outStream.close()
        bitmap.recycle()

        return file
    }
}
