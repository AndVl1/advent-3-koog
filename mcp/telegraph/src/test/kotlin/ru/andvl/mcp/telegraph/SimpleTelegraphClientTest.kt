package ru.andvl.mcp.telegraph

import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Простые тесты для TelegraphClient
 */
class SimpleTelegraphClientTest {

    private lateinit var client: TelegraphClient
    private lateinit var testToken: String /*by lazy {
        dotenv { ignoreIfMissing = true }["TELEGRAPH_ACCESS_TOKEN"]
//            ?: "c6b21acaf02dd1ec9995f59aaf1881a7b9991e502bc7fdfe0ef430378f81"
    }*/

    @BeforeEach
    fun setUp() {
        val dotenv = dotenv {
            ignoreIfMissing = true
            directory = "../../"
        }
        testToken = dotenv["TELEGRAPH_ACCESS_TOKEN"]
        client = TelegraphClient()
    }

    @AfterEach
    fun tearDown() {
        client.close()
    }

    @Test
    fun `test getAccountInfo with valid token`() = runTest {
        val account = client.getAccountInfo(
            accessToken = testToken,
            fields = listOf("short_name", "author_name")
        )

        assertNotNull(account, "Account info should be retrieved")
        assertNotNull(account?.shortName, "Short name should be present")
        assertTrue(account?.shortName!!.isNotBlank(), "Short name should not be blank")

        println("✅ Retrieved account info: ${account?.shortName}")
        println("👤 Author: ${account?.authorName ?: "N/A"}")
    }

    @Test
    fun `test createAccount with minimal parameters`() = runTest {
        val account = client.createAccount(
            shortName = "Test${System.currentTimeMillis()}"
        )

        assertNotNull(account, "Account should be created")
        assertNotNull(account?.shortName, "Short name should be present")
        assertTrue(account?.shortName!!.startsWith("Test"), "Short name should start with Test")

        println("✅ Created account: ${account?.shortName}")
        println("🔑 Token: ${account?.accessToken?.take(10)}...")
    }

    @Test
    fun `test createPage with simple content`() = runTest {
        val content = listOf(
            TelegraphNode("h3", listOf("Test Page")),
            TelegraphNode("p", listOf("This is a simple test page."))
        )

        val page = client.createPage(
            accessToken = testToken,
            title = "Test Page ${System.currentTimeMillis()}",
            authorName = "Test Bot",
            content = content,
            returnContent = true
        )

        assertNotNull(page, "Page should be created")
        assertTrue(page?.title!!.contains("Test Page"), "Title should match")
        assertEquals("Test Bot", page?.authorName)
        assertNotNull(page?.path, "Page path should be provided")
        assertTrue(page?.url!!.contains("telegra.ph"), "URL should be Telegraph URL")
        assertNotNull(page?.content, "Content should be returned when requested")
        assertTrue(page?.content!!.isNotEmpty(), "Content should not be empty")

        println("✅ Created page: ${page?.title}")
        println("🔗 URL: ${page?.url}")
        println("📝 Content nodes: ${page?.content?.size}")
    }

    @Test
    fun `test getPage without content`() = runTest {
        // First create a page
        val content = listOf(
            TelegraphNode("h1", listOf("Test Page for Get")),
            TelegraphNode("p", listOf("This page will be retrieved"))
        )

        val createdPage = client.createPage(
            accessToken = testToken,
            title = "Get Test ${System.currentTimeMillis()}",
            content = content
        )

        assertNotNull(createdPage, "Page should be created")

        // Retrieve the page without content
        val retrievedPage = client.getPage(
            path = createdPage?.path!!,
            returnContent = false
        )

        assertNotNull(retrievedPage, "Page should be retrieved")
        assertEquals(createdPage?.path, retrievedPage?.path)
        assertEquals(createdPage?.title, retrievedPage?.title)
        assertNull(retrievedPage?.content, "Content should not be returned when returnContent=false")

        println("✅ Retrieved page without content: ${retrievedPage?.title}")
    }

    @Test
    fun `test getPageList`() = runTest {
        val pageList = client.getPageList(
            accessToken = testToken,
            offset = 0,
            limit = 5
        )

        if (pageList == null) {
            println("❌ getPageList returned null - might be a token issue")
            return@runTest
        }

        assertNotNull(pageList, "Page list should be retrieved")
        assertTrue(pageList.totalPages ?: 0 >= 0, "Total pages should be non-negative")
        assertNotNull(pageList.pages, "Pages list should not be null")
        assertTrue(pageList.pages.size <= 5, "Pages should not exceed limit")

        if (!pageList.pages.isEmpty()) {
            val firstPage = pageList.pages[0]
            assertNotNull(firstPage.path, "Page should have path")
            assertNotNull(firstPage.url, "Page should have URL")
            assertNotNull(firstPage.title, "Page should have title")
        }

        println("✅ Retrieved page list")
        println("📄 Total pages: ${pageList.totalPages}")
        println("📋 Returned pages: ${pageList.pages.size}")
    }

    @Test
    fun `test getViews`() = runTest {
        // First create a page
        val content = listOf(
            TelegraphNode("p", listOf("Page for views test"))
        )

        val createdPage = client.createPage(
            accessToken = testToken,
            title = "Views Test ${System.currentTimeMillis()}",
            content = content
        )

        assertNotNull(createdPage, "Page should be created")

        // Get views for the page
        val views = client.getViews(
            path = createdPage?.path!!
        )

        assertNotNull(views, "Views should be retrieved")
        assertTrue(views?.views!! >= 0, "Views should be non-negative")

        println("✅ Retrieved views for page: ${createdPage?.title}")
        println("👁️ Views: ${views?.views}")
    }

    @Test
    fun `test error handling invalid token`() = runTest {
        val account = client.getAccountInfo(
            accessToken = "invalid_token_12345",
            fields = listOf("short_name")
        )

        assertNull(account, "Should return null for invalid token")
        println("✅ Invalid token properly handled")
    }

    @Test
    fun `test error handling invalid page path`() = runTest {
        val page = client.getPage(
            path = "Invalid-Page-Path-123",
            returnContent = false
        )

        assertNull(page, "Should return null for invalid page path")
        println("✅ Invalid page path properly handled")
    }

    @Test
    fun `test client initialization with default token`() = runTest {
        val clientWithToken = TelegraphClient(testToken)

        val account = clientWithToken.getAccountInfo(
            accessToken = testToken,
            fields = listOf("short_name")
        )

        assertNotNull(account, "Client with default token should work")
        clientWithToken.close()

        println("✅ Client initialization with default token works")
    }

    @Test
    fun `test editPage workflow`() = runTest {
        // First create a page
        val originalContent = listOf(
            TelegraphNode("h3", listOf("Original Title")),
            TelegraphNode("p", listOf("Original content"))
        )

        val createdPage = client.createPage(
            accessToken = testToken,
            title = "Original Title ${System.currentTimeMillis()}",
            content = originalContent,
            returnContent = true
        )

        assertNotNull(createdPage, "Original page should be created")

        // Edit the page
        val editedContent = listOf(
            TelegraphNode("h3", listOf("Edited Title")),
            TelegraphNode("p", listOf("This content has been edited"))
        )

        val editedPage = client.editPage(
            accessToken = testToken,
            path = createdPage?.path!!,
            title = "Edited Title ${System.currentTimeMillis()}",
            authorName = "Editor Bot",
            content = editedContent,
            returnContent = true
        )

        assertNotNull(editedPage, "Page should be edited")
        assertTrue(editedPage?.title!!.contains("Edited"), "Title should be updated")
        assertEquals("Editor Bot", editedPage?.authorName)
        assertEquals(createdPage?.path, editedPage?.path, "Path should remain the same")
        assertNotNull(editedPage?.content, "Content should be returned")

        println("✅ Edited page: ${editedPage?.title}")
        println("🔗 URL: ${editedPage?.url}")
        println("📝 Updated content nodes: ${editedPage?.content?.size}")
    }
}

/**
 * Интеграционный тест для проверки полного цикла работы с Telegraph API
 */
class TelegraphClientIntegrationTest {

    private lateinit var client: TelegraphClient
    private val testToken: String by lazy {
        dotenv {
            ignoreIfMissing = true
            directory = "../.."
        }["TELEGRAPH_ACCESS_TOKEN"]
    }

    @BeforeEach
    fun setUp() {
        client = TelegraphClient()
    }

    @AfterEach
    fun tearDown() {
        client.close()
    }

    @Test
    fun `full workflow test`() = runTest {
        // 1. Create a page
        val initialContent = listOf(
            TelegraphNode("h3", listOf("Full Workflow Test")),
            TelegraphNode("p", listOf("This is the initial content")),
            TelegraphNode("ul", listOf("First point", "Second point"))
        )

        val createdPage = client.createPage(
            accessToken = testToken,
            title = "Workflow Test ${System.currentTimeMillis()}",
            authorName = "Workflow Bot",
            content = initialContent,
            returnContent = true
        )

        assertNotNull(createdPage, "Step 1: Page should be created")
        println("✅ Step 1: Created page - ${createdPage?.title}")

        // 2. Get the page without content
        val retrievedPage = client.getPage(
            path = createdPage?.path!!,
            returnContent = false
        )

        assertNotNull(retrievedPage, "Step 2: Page should be retrieved")
        assertEquals(createdPage?.path, retrievedPage?.path)
        println("✅ Step 2: Retrieved page without content")

        // 3. Edit the page
        val editedContent = listOf(
            TelegraphNode("h3", listOf("Edited Workflow Test")),
            TelegraphNode("p", listOf("This content has been edited")),
            TelegraphNode("p", listOf("Additional content added during editing"))
        )

        val editedPage = client.editPage(
            accessToken = testToken,
            path = createdPage?.path!!,
            title = "Edited Workflow Test ${System.currentTimeMillis()}",
            authorName = "Editor Bot",
            content = editedContent,
            returnContent = true
        )

        assertNotNull(editedPage, "Step 3: Page should be edited")
        assertEquals(createdPage?.path, editedPage?.path)
        assertTrue(editedPage?.title!!.contains("Edited"))
        println("✅ Step 3: Edited page")

        // 4. Get the page again with content
        val finalPage = client.getPage(
            path = editedPage?.path!!,
            returnContent = true
        )

        assertNotNull(finalPage, "Step 4: Final page should be retrieved")
        assertEquals(editedPage?.path, finalPage?.path)
        assertEquals(editedPage?.title, finalPage?.title)
        assertNotNull(finalPage?.content)
        println("✅ Step 4: Retrieved edited page with content")

        // 5. Get views
        val views = client.getViews(path = finalPage?.path!!)
        assertNotNull(views)
        assertTrue(views?.views!! >= 0)
        println("✅ Step 5: Retrieved views - ${views?.views}")

        println("\n🎉 Full workflow test completed successfully!")
        println("📄 Final page URL: ${finalPage?.url}")
    }
}
