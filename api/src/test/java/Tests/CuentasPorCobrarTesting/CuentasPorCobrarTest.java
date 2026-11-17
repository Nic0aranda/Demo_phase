package Tests.CuentasPorCobrarTesting;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.Select;

import java.time.Duration;
import java.util.List;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

import Tests.LoginTesting.LoginHelper;

public class CuentasPorCobrarTest {

    // URL de la pantalla de cuentas por cobrar
    private static final String URL_CUENTAS =
            "http://localhost:5500/views/compartidas/cuentas-por-cobrar.html";
    // XPaths comunes a todos los tests
    private static final String XPATH_TABLA = "//table[contains(@class,'data-table')]";
    private static final String XPATH_FILAS = "//tbody[@id='carteraTableBody']/tr[not(contains(@class,'table-message'))]";
    private static final String XPATH_CELDAS_ESTADO = XPATH_FILAS + "/td[8]";

    private static void esperarFilasCargadas(WebDriverWait wait) {
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath(XPATH_FILAS)
        ));
    }

    //CC-01
    @Test
    public void VisualizarTablaCuentasPorCobrar() {

        // Inicializar el navegador
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();

        try {

            // Iniciar sesión usando el helper existente
            LoginHelper.iniciarSesion(driver);

            // Navegar a la pantalla de cuentas por cobrar
            driver.get(URL_CUENTAS);

            // Configurar el wait
            WebDriverWait wait = new WebDriverWait(
                    driver,
                    Duration.ofSeconds(10)
            );

            // Esperar a que la tabla sea visible
            WebElement tabla = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("/html/body/div[1]/main/section[2]/table")
                    )
            );

            // Comprobar que la tabla es visible
            assertTrue(
                    tabla.isDisplayed(),
                    "La tabla de cuentas por cobrar no es visible"
            );

            // Buscar los encabezados de la tabla
            List<WebElement> encabezados = driver.findElements(
                    By.xpath("/html/body/div[1]/main/section[2]/table/thead")
            );

            // Comprobar que la tabla tiene encabezados
            assertFalse(
                    encabezados.isEmpty(),
                    "La tabla no tiene encabezados"
            );

            // Buscar las filas del cuerpo de la tabla
            List<WebElement> filas = driver.findElements(
                    By.xpath("/html/body/div[1]/main/section[2]/table/tbody/tr[1]")
            );

        } finally {

            // Cerrar el navegador
            driver.quit();

        }

    }

    //CC-02
    @Test
    public void ConsultarCuentaPendiente() {

        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_CUENTAS);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // Esperar a que cargue la tabla
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));
            esperarFilasCargadas(wait);

            // Filtrar por estado AL_DIA
            new Select(driver.findElement(By.id("carteraEstado"))).selectByValue("AL_DIA");

            // Esperar que aparezca al menos una fila con estado "Al día"
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath(XPATH_FILAS + "[td[8][normalize-space()='Al día']]")
            ));

            // Verificar la primera fila visible
            WebElement celdaEstado = driver.findElement(By.xpath(XPATH_CELDAS_ESTADO + "[1]"));
            assertEquals(
                    "Al día",
                    celdaEstado.getText().trim(),
                    "La cuenta encontrada no está en estado Al día"
            );

        } finally {
            driver.quit();
        }
    }

    //CC-03
    @Test
    public void ConsultarCuentaPagada() {

        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_CUENTAS);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));
            esperarFilasCargadas(wait);

            // Filtrar por estado PAGADA
            new Select(driver.findElement(By.id("carteraEstado"))).selectByValue("PAGADA");

            // Esperar fila con estado "Pagada"
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath(XPATH_FILAS + "[td[8][normalize-space()='Pagada']]")
            ));

            WebElement celdaEstado = driver.findElement(By.xpath(XPATH_CELDAS_ESTADO + "[1]"));
            assertEquals(
                    "Pagada",
                    celdaEstado.getText().trim(),
                    "La cuenta encontrada no está en estado Pagada"
            );

        } finally {
            driver.quit();
        }
    }
    //CC-04
    @Test
    public void RegistrarPagoCuenta() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_CUENTAS);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // 1. Esperar a que la tabla cargue
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(XPATH_FILAS)));


            // 2. Hacer clic en el botón de "Registrar Pago" de la primera fila.
            WebElement botonPagar = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("/html/body/div[1]/main/section[2]/table/tbody/tr[1]/td[9]/button")
            ));
            botonPagar.click();

            // 3. Esperar a que se abra el modal de "Registrar pago"
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("/html/body/div[3]")
            ));

            // 4. Llenar los campos del modal.
            WebElement inputMonto = driver.findElement(
                    By.xpath("/html/body/div[3]/div/form/div[1]/div[1]/input")
            );
            inputMonto.clear();
            inputMonto.sendKeys("5000"); // Abonamos un monto de prueba

            // Seleccionar Medio de Pago
            WebElement selectMedioPago = driver.findElement(
                    By.xpath("/html/body/div[3]/div/form/div[1]/div[2]/select")
            );
            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "arguments[0].value = 'TRANSFERENCIA'; arguments[0].dispatchEvent(new Event('change'));",
                    selectMedioPago
            );

            // Llenar Referencia
            WebElement inputReferencia = driver.findElement(
                    By.xpath("/html/body/div[3]/div/form/div[1]/div[4]/input")
            );
            inputReferencia.sendKeys("TEST-001");

            // Llenar Observación
            WebElement inputObservacion = driver.findElement(
                    By.xpath("/html/body/div[3]/div/form/div[2]/textarea")
            );
            inputObservacion.sendKeys("Pago automatizado de prueba");

            // 5. Click en "Registrar pago" (el botón de acción del modal)
            WebElement botonConfirmar = driver.findElement(
                    By.xpath("/html/body/div[3]/div/form/div[3]/button[2]")
            );
            botonConfirmar.click();

            // 6. Validar que el modal se cerró o que la tabla se actualizó.
            wait.until(ExpectedConditions.invisibilityOfElementLocated(
                    By.xpath("//h2[contains(normalize-space(), 'Registrar pago')]")
            ));

        } finally {
            driver.quit();
        }
    }

    @Test
    public void RedireccionGestionCobranza() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_CUENTAS);

            // Esperar a que la página cargue
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h1[contains(text(), 'Cuentas por cobrar')]")));

            // Hacer clic en el botón "cobranza"
            WebElement btnGestion = driver.findElement(
                    By.xpath("//a[contains(text(),'cobranza')]")
            );
            btnGestion.click();

            // Validar la redirección
            wait.until(ExpectedConditions.urlContains("gestion-cobranza"));

            assertTrue(driver.getCurrentUrl().contains("gestion-cobranza"),
                    "No se redirigió correctamente a la gestión de cobranza");

        } finally {
            driver.quit();
        }
    }
    @Test
    public void VisualizarAuditoriaTemporal() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_CUENTAS);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//h1[contains(text(), 'Cuentas por cobrar')]")));

            WebElement btnAuditoria = driver.findElement(
                    By.xpath("//a[contains(text(),'Audit')]")
            );
            btnAuditoria.click();

            // Validar la redirección
            wait.until(ExpectedConditions.urlContains("auditoria"));

            // Validar que el título de la página de auditoría cargue
            WebElement tituloAuditoria = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//h1[contains(text(), 'Auditoría temporal')]")
            ));

            assertTrue(tituloAuditoria.isDisplayed(), "No se cargó la página de auditoría");

        } finally {
            driver.quit();
        }
    }


}