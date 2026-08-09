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
 * End-to-end coverage of the main CSV import UI flows against embedded H2,
 * including full-stack UPDATE and DELETE on existing tables.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportUiE2ETest extends PlaywrightE2ESupport {

    private static String sharedTable;
    /** Dedicated table for UPDATE/DELETE (no duplicate-key inserts). */
    private static String crudTable;

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
        assertThat(page.locator("#rowsAffected").innerText().trim()).isEqualTo("3");
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
        assertThat(page.locator("#rowsAffected").innerText().trim()).isEqualTo("3");
    }

    @Test
    @Order(5)
    @DisplayName("Existing-table mode lists table and inserts more rows")
    void existingTableInsertSucceeds() {
        assertThat(sharedTable).isNotBlank();
        openExistingPreview(sharedTable, simpleCsv());
        assertThat(page.locator("#existingModeBadge").innerText()).containsIgnoringCase("INSERT");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Execute Operation")).click();
        assertResultSuccess();
        assertThat(bodyText()).containsIgnoringCase("Import successful");
        assertThat(page.locator("#rowsAffected").innerText().trim()).isEqualTo("3");
    }

    @Test
    @Order(6)
    @DisplayName("Seed dedicated table for UPDATE/DELETE lifecycle")
    void seedCrudTableForUpdateDelete() {
        crudTable = uniqueTable("E2E_CRUD_");
        uploadCreateTable(crudTable, simpleCsv());
        assertThat(page.locator("#rowsAffected").innerText().trim()).isEqualTo("3");
    }

    @Test
    @Order(7)
    @DisplayName("Existing-table UPDATE by key changes rows")
    void existingTableUpdateSucceeds() {
        assertThat(crudTable).as("seedCrudTableForUpdateDelete must run first").isNotBlank();

        openExistingPreview(crudTable, updateCsv());
        page.locator("#operationSelect").selectOption("UPDATE");
        page.waitForFunction("() => document.getElementById('operationValue')?.value === 'UPDATE'");
        assertThat(page.locator("#existingModeBadge").innerText()).containsIgnoringCase("UPDATE");
        page.locator("select[name='keyColumns']").selectOption("ID");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Execute Operation")).click();

        assertResultSuccess();
        assertThat(bodyText()).containsIgnoringCase("Update successful");
        assertThat(page.locator("#rowsAffected").innerText().trim()).isEqualTo("2");
        assertThat(page.locator("#resultSql").innerText()).containsIgnoringCase("UPDATE");
        assertThat(page.locator("#resultSql").innerText()).containsIgnoringCase("WHERE");
    }

    @Test
    @Order(8)
    @DisplayName("Existing-table DELETE by key removes matching rows")
    void existingTableDeleteSucceeds() {
        assertThat(crudTable).isNotBlank();

        openExistingPreview(crudTable, deleteCsv());
        page.locator("#operationSelect").selectOption("DELETE");
        page.waitForFunction("() => document.getElementById('operationValue')?.value === 'DELETE'");
        assertThat(page.locator("#existingModeBadge").innerText()).containsIgnoringCase("DELETE");
        page.locator("select[name='keyColumns']").selectOption("ID");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Execute Operation")).click();

        assertResultSuccess();
        assertThat(bodyText()).containsIgnoringCase("Delete successful");
        assertThat(page.locator("#rowsAffected").innerText().trim()).isEqualTo("1");
        assertThat(page.locator("#resultSql").innerText()).containsIgnoringCase("DELETE");
    }

    @Test
    @Order(9)
    @DisplayName("UPDATE without key columns stays on preview with error")
    void updateWithoutKeysShowsValidationError() {
        assertThat(crudTable).isNotBlank();

        openExistingPreview(crudTable, updateCsv());
        page.locator("#operationSelect").selectOption("UPDATE");
        page.waitForFunction("() => document.getElementById('operationValue')?.value === 'UPDATE'");
        page.locator("select[name='keyColumns']").evaluate(
                "el => { Array.from(el.options).forEach(o => o.selected = false); el.dispatchEvent(new Event('change')); }"
        );

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Execute Operation")).click();

        page.waitForSelector("text=Preview and Mapping");
        assertThat(bodyText()).containsAnyOf("key column", "Key column", "key columns");
        assertThat(page.locator("text=Import Result").count()).isZero();
    }

    @Test
    @Order(10)
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
        assertThat(page.locator("#rowsAffected").innerText().trim()).isEqualTo("5");
        assertThat(bodyText()).containsIgnoringCase("DECIMAL");
    }

    private void openExistingPreview(String tableName, java.nio.file.Path csv) {
        goHome();
        page.locator("#modeExisting").check();
        page.waitForFunction("() => document.getElementById('useExistingTable')?.value === 'true'");
        page.locator("#file").setInputFiles(csv);
        page.locator("#library").fill("TESTLIB");
        page.locator("#csvDelimiter").selectOption(";");
        page.waitForFunction(
                "name => { const s = document.getElementById('existingTableSelect'); "
                        + "return s && !s.disabled && Array.from(s.options).some(o => o.value === name); }",
                tableName,
                new Page.WaitForFunctionOptions().setTimeout(15_000)
        );
        page.locator("#existingTableSelect").selectOption(tableName);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Upload and Analyze")).click();
        page.waitForSelector("text=Preview and Mapping");
        assertThat(bodyText()).contains("Auto-Mapping to Existing Table");
    }
}
