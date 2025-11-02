package ru.andvl.mcp.googledocs

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.docs.v1.Docs
import com.google.api.services.docs.v1.DocsScopes
import com.google.api.services.docs.v1.model.Document
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.FileInputStream

/**
 * Клиент для работы с Google Docs API
 */
class GoogleDocsClient(
    private val serviceAccountPath: String? = null,
    private val serviceAccountJson: String? = null
) {
    private val logger = LoggerFactory.getLogger(GoogleDocsClient::class.java)
    private val applicationName = "Homework Checker MCP Server"

    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private val credentials: GoogleCredentials by lazy {
        createCredentials()
    }

    private val docsService: Docs by lazy {
        Docs.Builder(httpTransport, jsonFactory, HttpCredentialsAdapter(credentials))
            .setApplicationName(applicationName)
            .build()
    }

    private val driveService: Drive by lazy {
        Drive.Builder(httpTransport, jsonFactory, HttpCredentialsAdapter(credentials))
            .setApplicationName(applicationName)
            .build()
    }

    private fun createCredentials(): GoogleCredentials {
        val scopes = listOf(DocsScopes.DOCUMENTS_READONLY, DriveScopes.DRIVE_READONLY)

        return when {
            serviceAccountJson != null -> {
                logger.info("📄 Использую JSON из строки для аутентификации")
                GoogleCredentials.fromStream(ByteArrayInputStream(serviceAccountJson.toByteArray()))
                    .createScoped(scopes)
            }
            serviceAccountPath != null -> {
                logger.info("📁 Использую файл $serviceAccountPath для аутентификации")
                GoogleCredentials.fromStream(FileInputStream(serviceAccountPath))
                    .createScoped(scopes)
            }
            else -> {
                logger.info("🔑 Использую Application Default Credentials")
                GoogleCredentials.getApplicationDefault().createScoped(scopes)
            }
        }
    }

    /**
     * Получить информацию о документе
     */
    suspend fun getDocumentInfo(documentId: String): Result<GoogleDocInfo> {
        return runCatching {
            val document = docsService.documents().get(documentId).execute()

            GoogleDocInfo(
                id = document.documentId,
                title = document.title ?: "Untitled Document",
                revisionId = document.revisionId,
                documentStyle = null // Упрощенная версия без обработки стилей
            )
        }
    }

    /**
     * Получить текстовое содержимое документа
     */
    suspend fun getDocumentContent(documentId: String): GoogleDocContent? {
        return try {
            val document = withContext(Dispatchers.IO) {
                docsService.documents().get(documentId).execute()
            }
            val content = withContext(Dispatchers.Default) {
                extractTextFromDocument(document)
            }

            GoogleDocContent(
                documentId = document.documentId,
                title = document.title ?: "Untitled Document",
                content = content,
                wordCount = content.split("\\s+".toRegex()).size,
                characterCount = content.length
            )
        } catch (e: Exception) {
            logger.error("Ошибка при получении содержимого документа $documentId: ${e.message}", e)
            null
        }
    }

    /**
     * Извлечь текст из структуры документа
     */
    private fun extractTextFromDocument(document: Document): String {
        val content = document.body?.content ?: return ""
        val textBuilder = StringBuilder()

        for (structuralElement in content) {
            when {
                structuralElement.paragraph != null -> {
                    val paragraph = structuralElement.paragraph
                    for (element in paragraph.elements ?: emptyList()) {
                        if (element.textRun != null) {
                            textBuilder.append(element.textRun.content ?: "")
                        }
                    }
                }
                structuralElement.table != null -> {
                    val table = structuralElement.table
                    for (row in table.tableRows ?: emptyList()) {
                        for (cell in row.tableCells ?: emptyList()) {
                            for (cellContent in cell.content ?: emptyList()) {
                                if (cellContent.paragraph != null) {
                                    for (element in cellContent.paragraph.elements ?: emptyList()) {
                                        if (element.textRun != null) {
                                            textBuilder.append(element.textRun.content ?: "")
                                        }
                                    }
                                }
                            }
                            textBuilder.append("\t") // Разделитель ячеек
                        }
                        textBuilder.append("\n") // Разделитель строк
                    }
                }
                structuralElement.sectionBreak != null -> {
                    textBuilder.append("\n\n") // Разрыв секции
                }
            }
        }

        return textBuilder.toString().trim()
    }

    /**
     * Получить список документов с доступом
     */
    suspend fun listAccessibleDocuments(maxResults: Int = 10): List<GoogleDocInfo> {
        return try {
            val query = "mimeType='application/vnd.google-apps.document'"
            val result = withContext(Dispatchers.IO) {
                driveService.files().list()
                    .setQ(query)
                    .setPageSize(maxResults)
                    .setFields("files(id,name,modifiedTime)")
                    .execute()
            }

            result.files?.mapNotNull { file ->
                GoogleDocInfo(
                    id = file.id,
                    title = file.name ?: "Untitled Document",
                    revisionId = null,
                    documentStyle = null,
                    modifiedTime = file.modifiedTime?.toString()
                )
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error("Ошибка при получении списка документов: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Извлечь ID документа из URL
     */
    fun extractDocumentId(url: String): String? {
        val regex = Regex("""/document/d/([a-zA-Z0-9-_]+)""")
        return regex.find(url)?.groupValues?.get(1)
    }

    /**
     * Проверить доступность документа
     */
    suspend fun isDocumentAccessible(documentId: String): Boolean {
        return try {
            docsService.documents().get(documentId).execute()
            true
        } catch (e: Exception) {
            logger.warn("Документ $documentId недоступен: ${e.message}")
            false
        }
    }
}

/**
 * Модели данных для Google Docs
 */
@Serializable
data class GoogleDocInfo(
    val id: String,
    val title: String,
    val revisionId: String?,
    val documentStyle: DocumentStyle?,
    val modifiedTime: String? = null
)

@Serializable
data class GoogleDocContent(
    val documentId: String,
    val title: String,
    val content: String,
    val wordCount: Int,
    val characterCount: Int
)

@Serializable
data class DocumentStyle(
    val backgroundColor: Color?
)

@Serializable
data class Color(
    val red: Float,
    val green: Float,
    val blue: Float
)
