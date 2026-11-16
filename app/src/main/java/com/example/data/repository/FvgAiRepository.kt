package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "FvgAiRepository"

data class OcrExtractionResult(
    val extractedText: String,
    val vendorName: String,
    val documentDate: String,
    val currency: String,
    val totalAmount: String,
    val confidenceNote: String,
    val lineSummary: String
)

class FvgAiRepository {

    private val assistantModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val ocrSchema = Schema.obj(
        properties = mapOf(
            "extractedText" to Schema.string(description = "Full verbatim OCR transcription of the document"),
            "vendorName" to Schema.string(description = "Supplier or merchant name if visible, else empty"),
            "documentDate" to Schema.string(description = "Document/invoice date in YYYY-MM-DD if visible, else empty"),
            "currency" to Schema.string(description = "3-letter currency code such as SDG, USD, SAR, EUR if visible, else empty"),
            "totalAmount" to Schema.string(description = "Numeric total amount without currency symbol, e.g. 1250.00"),
            "confidenceNote" to Schema.string(description = "Short note describing OCR legibility or fields needing human check"),
            "lineSummary" to Schema.string(description = "Concise list of detected line items, quantities, and unit prices")
        )
    )

    private val ocrModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = ocrSchema
            }
        )
    }

    fun streamGroundedExplanation(
        question: String,
        systemDataContext: String,
        languageCode: String,
        aiMode: String = "financial" // "general", "financial", "research"
    ): Flow<String> = flow {
        val langInstruction = if (languageCode == "ar") {
            "Respond clearly in Arabic (العربية), citing exact numbers and source labels from the provided FVG system data."
        } else {
            "Respond clearly in English, citing exact numbers and source labels from the provided FVG system data."
        }
        val modeInstruction = when (aiMode) {
            "general" -> "MODE: General & Educational Management Accounting Guide. Explain accounting, costing, and inflation concepts clearly and cite workspace data only where present."
            "research" -> "MODE: Academic Research Assistant (Zahra Yousif Elabid Wagiealla Ahmed's Master Thesis on Nobo Factory). Help structure methodology, variables, and survey instruments WITHOUT EVER inventing factory figures or survey responses; clearly flag uncollected data as [Pending Field Data]."
            else -> "MODE: Financial Data Analyst. Analyze recorded prices, inflation baskets, costs, margins, variances, risk alerts, and accounting statements strictly from the workspace data below."
        }
        val prompt = """
            You are FVG Financial Vision Guide's analytical assistant.
            $modeInstruction
            STRICT RULES:
            1. Rely ONLY on verified FVG system data provided below for quantitative claims.
            2. NEVER invent figures, FX rates, Nobo factory field numbers, survey results, or estimates. If data is absent, explicitly state that it is not recorded yet.
            3. Identify the exact source module/table for every number you cite.
            4. Do NOT claim to modify database records or execute accounting postings.
            5. $langInstruction

            --- VERIFIED ORGANIZATION SYSTEM DATA ---
            $systemDataContext
            --- END SYSTEM DATA ---

            User Question: $question
        """.trimIndent()

        val acc = StringBuilder()
        try {
            withTimeout(60_000L) {
                assistantModel.generateContentStream(prompt).collect { chunk ->
                    val t = chunk.text.orEmpty()
                    if (t.isNotEmpty()) {
                        acc.append(t)
                        emit(acc.toString())
                    }
                }
            }
            if (acc.isBlank()) {
                throw IllegalStateException("AI assistant returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI assistant request timed out", e)
            throw IllegalStateException("AI assistant timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI assistant generation failed", e)
            throw e
        }
    }

    suspend fun extractInvoiceDocument(bitmap: Bitmap): OcrExtractionResult {
        try {
            val response = withTimeout(60_000L) {
                ocrModel.generateContent(
                    content {
                        image(bitmap)
                        text(
                            "Extract invoice, receipt, or price sheet fields for human review. " +
                                "Do not guess unreadable numbers; return empty string if unclear and mention uncertainty in confidenceNote."
                        )
                    }
                )
            }
            val jsonStr = response.text ?: throw IllegalStateException("OCR returned empty output")
            val json = JSONObject(jsonStr)
            return OcrExtractionResult(
                extractedText = json.optString("extractedText", ""),
                vendorName = json.optString("vendorName", ""),
                documentDate = json.optString("documentDate", ""),
                currency = json.optString("currency", ""),
                totalAmount = json.optString("totalAmount", ""),
                confidenceNote = json.optString("confidenceNote", "Verify extracted numbers before posting."),
                lineSummary = json.optString("lineSummary", "")
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "OCR extraction timed out", e)
            throw IllegalStateException("Document OCR timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OCR extraction failed", e)
            throw e
        }
    }
}
