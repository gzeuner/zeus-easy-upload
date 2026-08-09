package com.zeus.upload.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared Playwright + Spring Boot harness for full browser E2E tests.
 * <p>
 * Run with: {@code mvn -Pe2e test}
 * First run downloads Chromium via Playwright.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class PlaywrightE2ESupport {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    protected int port;

    protected BrowserContext context;
    protected Page page;

    @BeforeAll
    static void startBrowser() {
        playwright = Playwright.create();
        boolean headless = !"false".equalsIgnoreCase(System.getenv().getOrDefault("E2E_HEADLESS", "true"));
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setSlowMo(Integer.parseInt(System.getenv().getOrDefault("E2E_SLOW_MO", "0"))));
    }

    @AfterAll
    static void stopBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openContext() {
        context = browser.newContext(new Browser.NewContextOptions().setLocale("en-US"));
        page = context.newPage();
        page.setDefaultTimeout(20_000);
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    protected String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    protected void goHome() {
        page.navigate(baseUrl() + "/");
        page.waitForSelector("text=CSV Import");
    }

    protected Path sampleCsv() {
        return Paths.get("src/main/resources/examples/test-import.csv").toAbsolutePath();
    }

    protected Path simpleCsv() {
        return Paths.get("src/test/resources/e2e/simple-import.csv").toAbsolutePath();
    }

    protected static String uniqueTable(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        // Keep short enough for IBM-i style names (10 chars) used in H2 dialect for columns,
        // but table names are not truncated to 10 in CREATE — still keep readable.
        return (prefix + suffix).substring(0, Math.min(18, prefix.length() + suffix.length())).toUpperCase();
    }

    protected String bodyText() {
        return page.locator("body").innerText();
    }

    protected void assertResultSuccess() {
        page.waitForSelector("text=Import Result");
        assertThat(page.locator(".alert-success").count()).as("success alert").isGreaterThan(0);
        assertThat(bodyText()).contains("SUCCESS");
    }

    protected void assertResultFailed() {
        page.waitForSelector("text=Import Result");
        assertThat(page.locator(".alert-danger").count()).as("failure alert").isGreaterThan(0);
        assertThat(bodyText()).contains("FAILED");
    }

    protected void clickNav(String linkText) {
        page.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName(linkText)).click();
    }
}
