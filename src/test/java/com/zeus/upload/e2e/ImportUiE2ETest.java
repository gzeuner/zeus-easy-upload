package com.zeus.upload.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * End-to-end coverage of the main CSV import UI flows against embedded H2.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportUiE2ETest extends PlaywrightE2ESupport {

    private static String sharedTable;

    @Test
    @Order(1)
    @DisplayName("Home page shows create/existing import modes")
    void homePageLoads() {
        goHome();
        assertThat(page.locator("#modeCreate").isChecked()).isTrue();
        assertThat(page.locator("#modeExisting").isVisible()).isTrue();
        assertThat(page.locator("#dropAndRecreate").isVisible()).isTrue();
        assertThat(page.locator("#library").inputValue()).isEqualToIgnoringCase("TESTLIB");
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Verbindungen")).count())
                .isGreaterThan(0);
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("CSV Import")).count())
                .isGreaterThan(0);
    }

    @Test
    @Order(2)
    @DisplayName("Create table + import succeeds with typed CSV")
    void createTableAndImportSuccess() {
        sharedTable = uniqueTable("E2E_CRT_");
        goHome();

        page.locator("#file").setInputFiles(simpleCsv());
        page.locator("#library").fill("TESTLIB");
        page.locator("#tableName").fill(sharedTable);
        page.locator("#csvDelimiter").selectOption(";");
        page.locator("#dropAndRecreate").check();

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Upload and Analyze")).click();

        page.waitForSelector("text=Preview and Mapping");
        assertThat(bodyText()).contains("Create table + INSERT");
        assertThat(bodyText()).contains("Column Proposals");
        assertThat(bodyText()).containsIgnoringCase("AMOUNT");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create Table and Import")).click();

        assertResultSuccess();
        assertThat(bodyText()).containsIgnoringCase("Import successful");
        assertThat(bodyText()).containsPattern("Inserted Rows:\\s*3");
        assertThat(bodyText()).containsIgnoringCase("CREATE TABLE");
    }

    @Test
    @Order(3)
    @DisplayName("Create without drop fails clearly when table already exists")
    void createWithoutDropShowsFriendlyError() {
        assertThat(sharedTable).as("previous create must run first").isNotBlank();
        goHome();

        page.locator("#file").setInputFiles(simpleCsv());
        page.locator("#library").fill("TESTLIB");
        page.locator("#tableName").fill(sharedTable);
        page.locator("#csvDelimiter").selectOption(";");
        if (page.locator("#dropAndRecreate").isChecked()) {
            page.locator("#dropAndRecreate").uncheck();
        }

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Upload and Analyze")).click();
        page.waitForSelector("text=Preview and Mapping");

        Locator previewDrop = page.locator("input[name='dropAndRecreate']");
        if (previewDrop.count() > 0 && previewDrop.first().isChecked()) {
            previewDrop.first().uncheck();
        }

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create Table and Import")).click();

        assertResultFailed();
        String message = bodyText();
        assertThat(message).containsIgnoringCase("already exists");
        assertThat(message).containsIgnoringCase("Drop table");
    }

    @Test
    @Order(4)
    @DisplayName("Drop and recreate re-imports into same table name")
    void dropAndRecreateSucceeds() {
        assertThat(sharedTable).isNotBlank();
        goHome();

        page.locator("#file").setInputFiles(simpleCsv());
        page.locator("#library").fill("TESTLIB");
        page.locator("#tableName").fill(sharedTable);
        page.locator("#csvDelimiter").selectOption(";");
        page.locator("#dropAndRecreate").check();

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Upload and Analyze")).click();
        page.waitForSelector("text=Preview and Mapping");

        Locator previewDrop = page.locator("input[name='dropAndRecreate']");
        if (previewDrop.count() > 0 && !previewDrop.first().isChecked()) {
            previewDrop.first().check();
        }

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create Table and Import")).click();

        assertResultSuccess();
        assertThat(bodyText()).containsPattern("Inserted Rows:\\s*3");
    }

    @Test
    @Order(5)
    @DisplayName("Existing-table mode lists table and inserts more rows")
    void existingTableInsertSucceeds() {
        assertThat(sharedTable).isNotBlank();
        goHome();

        page.locator("#modeExisting").check();
        page.waitForFunction("() => document.getElementById('useExistingTable')?.value === 'true'");

        page.locator("#file").setInputFiles(simpleCsv());
        page.locator("#library").fill("TESTLIB");
        page.locator("#csvDelimiter").selectOption(";");

        page.waitForFunction(
                "name => { const s = document.getElementById('existingTableSelect'); "
                        + "return s && !s.disabled && Array.from(s.options).some(o => o.value === name); }",
                sharedTable,
                new Page.WaitForFunctionOptions().setTimeout(15_000)
        );
        page.locator("#existingTableSelect").selectOption(sharedTable);

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Upload and Analyze")).click();

        page.waitForSelector("text=Preview and Mapping");
        assertThat(bodyText()).contains("Existing table INSERT");
        assertThat(bodyText()).contains("Auto-Mapping to Existing Table");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Execute Operation")).click();

        assertResultSuccess();
        assertThat(bodyText()).containsPattern("Inserted Rows:\\s*3");
    }

    @Test
    @Order(6)
    @DisplayName("Full test-import.csv (decimals/dates) creates successfully")
    void fullSampleCsvCreateSucceeds() {
        String table = uniqueTable("E2E_FULL_");
        goHome();

        page.locator("#file").setInputFiles(sampleCsv());
        page.locator("#library").fill("TESTLIB");
        page.locator("#tableName").fill(table);
        page.locator("#csvDelimiter").selectOption(";");
        page.locator("#dropAndRecreate").check();

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Upload and Analyze")).click();
        page.waitForSelector("text=Preview and Mapping");

        assertThat(bodyText()).containsIgnoringCase("AMOUNT");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create Table and Import")).click();

        assertResultSuccess();
        assertThat(bodyText()).containsPattern("Inserted Rows:\\s*5");
        assertThat(bodyText()).containsIgnoringCase("DECIMAL");
    }
}
