package com.zeus.upload.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Browser coverage for the connection profiles page, including the
 * "Verbindung testen" button against saved JDBC and REST profiles.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConnectionsUiE2ETest extends PlaywrightE2ESupport {

    private static String h2ProfileName;
    private static String restProfileName;

    @Test
    @Order(1)
    @DisplayName("Connections page loads and shows form with test button")
    void connectionsPageLoads() {
        page.navigate(baseUrl() + "/connections");
        page.waitForSelector("text=Verbindungsprofile");
        assertThat(page.locator("#connectionForm").isVisible()).isTrue();
        assertThat(page.locator("#connectionName").isVisible()).isTrue();
        assertThat(page.locator("#connectionType").isVisible()).isTrue();
        assertThat(page.locator("#connectionEndpoint").isVisible()).isTrue();
        assertThat(page.locator("#testConnection").isVisible()).isTrue();
        assertThat(page.locator("#testConnection").isEnabled()).isTrue();
        assertThat(page.locator("#saveConnection").isEnabled()).isTrue();
        // Encryption is configured in test profile with dummy master key
        assertThat(bodyText()).doesNotContain("Verschlüsselung ist nicht eingerichtet");
    }

    @Test
    @Order(2)
    @DisplayName("Nav links between Import and Verbindungen work")
    void navigationWorks() {
        goHome();
        // Prefer navbar — the form also links to /connections in help text.
        page.locator("nav").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Verbindungen")).click();
        page.waitForSelector("text=Verbindungsprofile");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Zum Import")).click();
        page.waitForSelector("text=CSV Import");
    }

    @Test
    @Order(3)
    @DisplayName("Test button without name shows guidance")
    void testWithoutNameShowsWarning() {
        page.navigate(baseUrl() + "/connections");
        page.waitForSelector("#testConnection");
        page.locator("#newConnection").click();
        page.locator("#connectionName").fill("");
        page.locator("#testConnection").click();
        waitForStatusMatching("Profil", "warning");
        assertThat(statusText()).containsIgnoringCase("Profil");
    }

    @Test
    @Order(4)
    @DisplayName("Test unsaved profile name returns not-found warning")
    void testUnsavedProfileShowsNotFound() {
        page.navigate(baseUrl() + "/connections");
        page.waitForSelector("#testConnection");
        page.locator("#newConnection").click();
        page.locator("#connectionName").fill("unsaved-profile-" + shortId());
        page.locator("#connectionEndpoint").fill("jdbc:h2:mem:unused;MODE=DB2");
        page.locator("#testConnection").click();
        // Skip the intermediate "Verbindung wird getestet …" info state.
        waitForStatusMatching("speichern|nicht gefunden", "warning");
        String status = statusText();
        assertThat(status).containsAnyOf("nicht gefunden", "zuerst speichern");
    }

    @Test
    @Order(5)
    @DisplayName("Save H2 JDBC profile and connection test succeeds")
    void saveH2ProfileAndTestSucceeds() {
        h2ProfileName = "e2e-h2-" + shortId();
        String jdbcUrl = "jdbc:h2:mem:e2e_conn_" + shortId()
                + ";MODE=DB2;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true";

        page.navigate(baseUrl() + "/connections");
        page.waitForSelector("#connectionForm");
        page.locator("#newConnection").click();
        page.locator("#connectionName").fill(h2ProfileName);
        page.locator("#connectionType").selectOption("DB2_400");
        page.locator("#connectionEndpoint").fill(jdbcUrl);
        page.locator("#connectionDescription").fill("E2E H2 connection test profile");
        page.locator("#connectionUsername").fill("sa");
        page.locator("#connectionSecret").fill("");

        page.locator("#saveConnection").click();
        waitForProfileInList(h2ProfileName);
        waitForStatusMatching("gespeichert", "success");

        page.locator("#testConnection").click();
        waitForStatusMatching("^OK:", "success");

        String status = statusText();
        assertThat(status).startsWith("OK:");
        assertThat(status).containsIgnoringCase("Connection successful");
        assertThat(status).containsIgnoringCase("H2");
        assertThat(status).contains("ms");
        // Secrets must never leak into status UI
        assertThat(status).doesNotContain("password=");
    }

    @Test
    @Order(6)
    @DisplayName("Connection test against unreachable JDBC endpoint fails clearly")
    void testUnreachableJdbcFails() {
        String name = "e2e-bad-jdbc-" + shortId();
        // Port 1 should refuse quickly without long DNS waits.
        String badUrl = "jdbc:h2:tcp://127.0.0.1:1/./e2e_unreachable";

        page.navigate(baseUrl() + "/connections");
        page.waitForSelector("#connectionForm");
        page.locator("#newConnection").click();
        page.locator("#connectionName").fill(name);
        page.locator("#connectionType").selectOption("DB2_400");
        page.locator("#connectionEndpoint").fill(badUrl);
        page.locator("#connectionUsername").fill("sa");
        page.locator("#connectionSecret").fill("irrelevant");

        page.locator("#saveConnection").click();
        waitForProfileInList(name);
        waitForStatusMatching("gespeichert", "success");

        page.locator("#testConnection").click();
        waitForStatusMatching(".", "danger");

        String status = statusText();
        assertThat(status).doesNotStartWith("OK:");
        assertThat(status.toLowerCase()).containsAnyOf("failed", "fehlgeschlagen", "connection");
        assertThat(status).doesNotContain("irrelevant");
    }

    @Test
    @Order(7)
    @DisplayName("Save REST profile pointing at this app and test succeeds")
    void saveRestProfileAndTestSucceeds() {
        restProfileName = "e2e-rest-" + shortId();
        // Hit the running Spring Boot app itself (200 HTML home page).
        String restUrl = baseUrl() + "/";

        page.navigate(baseUrl() + "/connections");
        page.waitForSelector("#connectionForm");
        page.locator("#newConnection").click();
        page.locator("#connectionName").fill(restProfileName);
        page.locator("#connectionType").selectOption("REST");
        page.locator("#connectionEndpoint").fill(restUrl);
        page.locator("#connectionDescription").fill("E2E REST self-check");
        page.locator("#connectionUsername").fill("");
        page.locator("#connectionSecret").fill("");

        page.locator("#saveConnection").click();
        waitForProfileInList(restProfileName);
        waitForStatusMatching("gespeichert", "success");

        page.locator("#testConnection").click();
        waitForStatusMatching("^OK:", "success");

        String status = statusText();
        assertThat(status).startsWith("OK:");
        assertThat(status).containsIgnoringCase("HTTP");
        assertThat(status).contains("ms");
    }

    @Test
    @Order(8)
    @DisplayName("Reload saved H2 profile from list and re-test succeeds")
    void loadSavedProfileFromListAndRetest() {
        assertThat(h2ProfileName).as("H2 profile from earlier test").isNotBlank();

        page.navigate(baseUrl() + "/connections");
        waitForProfileInList(h2ProfileName);

        page.locator("#connectionList button").filter(
                new Locator.FilterOptions().setHasText(h2ProfileName)
        ).first().click();

        page.waitForFunction(
                "name => document.getElementById('connectionName')?.value === name",
                h2ProfileName,
                new Page.WaitForFunctionOptions().setTimeout(10_000)
        );
        // Username/secret stay empty on load (secrets never returned)
        assertThat(page.locator("#connectionUsername").inputValue()).isEmpty();
        assertThat(page.locator("#connectionSecret").inputValue()).isEmpty();

        page.locator("#testConnection").click();
        waitForStatusMatching("^OK:", "success");
        assertThat(statusText()).startsWith("OK:");
    }

    private void waitForProfileInList(String profileName) {
        page.waitForFunction(
                "name => Array.from(document.querySelectorAll('#connectionList button'))"
                        + ".some(b => (b.textContent || '').includes(name))",
                profileName,
                new Page.WaitForFunctionOptions().setTimeout(15_000)
        );
    }

    /**
     * Wait until {@code #connectionStatus} shows a non-empty message of the given Bootstrap
     * alert kind and whose text matches {@code textRegex} (case-insensitive).
     */
    private void waitForStatusMatching(String textRegex, String alertKind) {
        page.waitForFunction(
                "([regex, kind]) => {"
                        + "  const el = document.getElementById('connectionStatus');"
                        + "  if (!el || el.classList.contains('d-none')) return false;"
                        + "  if (!el.classList.contains('alert-' + kind)) return false;"
                        + "  const text = (el.textContent || '').trim();"
                        + "  if (!text) return false;"
                        // Ignore intermediate "testing…" info once replaced by final status.
                        + "  if (/getestet/i.test(text) && kind !== 'info') return false;"
                        + "  return new RegExp(regex, 'i').test(text);"
                        + "}",
                java.util.List.of(textRegex, alertKind),
                new Page.WaitForFunctionOptions().setTimeout(25_000)
        );
    }

    private String statusText() {
        return page.locator("#connectionStatus").innerText().trim();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
