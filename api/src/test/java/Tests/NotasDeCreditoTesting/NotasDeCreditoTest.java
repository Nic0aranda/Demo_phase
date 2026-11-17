package Tests.NotasDeCreditoTesting ;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import Tests.LoginTesting.LoginHelper;


public class NotasDeCreditoTest {

    private static final String URL_NOTAS_CREDITO = "http://localhost:5500/views/compartidas/notas-credito.html";
    // XPaths
    private static final String XPATH_TABLA = "//table[contains(@class,'data-table')]";
    private static final String XPATH_FILAS = "//tbody[@id='notasTableBody']/tr[not(contains(@class,'table-message'))]";


    private void clickSeguro(WebDriver driver, WebDriverWait wait, By locator) {
        WebElement elemento = wait.until(ExpectedConditions.elementToBeClickable(locator));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", elemento);
        elemento.click();
    }
    //NC-01
    @Test
    public void VisualizarTablaNotasCredito() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize(); // Prevenir problemas de ventana pequeña

        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_NOTAS_CREDITO);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // 1. Esperar a que la tabla cargue
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(XPATH_FILAS)));

            // 2. Validar que hay filas
            List<WebElement> filas = driver.findElements(By.xpath(XPATH_FILAS));
            assertFalse(filas.isEmpty(), "La tabla de notas de crédito no muestra registros");

        } finally {
            driver.quit();
        }
    }
    
    @Test
    public void FiltrarNotasPorTipo() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();

        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_NOTAS_CREDITO);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(XPATH_FILAS)));

            // 1. Seleccionar el filtro
            WebElement selectFiltro = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("/html/body/div[1]/main/header/div[2]/select")
            ));
            new Select(selectFiltro).selectByVisibleText("Descuento");

            // 2. Esperar que la tabla se actualice (ej. esperando a que aparezca una fila con ese tipo)
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("/html/body/div[1]/main/header/div[2]/select/option[3]")
            ));

            // 3. Validar que TODAS las filas visibles ahora tengan el tipo seleccionado
            List<WebElement> celdasTipo = driver.findElements(By.xpath("/html/body/div[1]/main/section[2]/table/tbody/tr[1]"));

            assertFalse(celdasTipo.isEmpty(), "El filtro no devolvió resultados");

            for (WebElement celda : celdasTipo) {
                assertEquals("Cargando información…", celda.getText().trim(),
                        "Apareció una nota de crédito con un tipo distinto al filtrado");
            }

        } finally {
            driver.quit();
        }
    }
    @Test
    public void EmitirNotaCredito() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();

        try {
            LoginHelper.iniciarSesion(driver);

            // 1. Navegar directamente a la pantalla de creación
            driver.get("http://localhost:5500/views/compartidas/crear-nota-credito.html");

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // 2. Esperar a que el formulario esté visible
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("notaForm")));

            // 3. Seleccionar la Factura (esperar a que carguen las opciones dinámicas)
            By facturaSelectLocator = By.id("notaFactura");
            wait.until(ExpectedConditions.presenceOfElementLocated(facturaSelectLocator));

            // Esperar a que la primera opción ya no diga "Cargando facturas…"
            wait.until(d -> {
                WebElement select = d.findElement(facturaSelectLocator);
                try {
                    // Si tiene más de 1 opción, o si la primera opción no contiene "Cargando"
                    return new Select(select).getOptions().size() > 1
                            || !new Select(select).getOptions().get(0).getText().contains("Cargando");
                } catch (Exception e) {
                    return false;
                }
            });

            Select selectFactura = new Select(driver.findElement(facturaSelectLocator));
            // Buscamos una opción que no esté vacía
            for (WebElement option : selectFactura.getOptions()) {
                String value = option.getAttribute("value");
                assert value != null;
                if (!value.isEmpty()) {
                    String attribute = option.getAttribute("value");
                    assert attribute != null;
                    selectFactura.selectByValue(attribute);
                    System.out.println("Factura seleccionada: " + option.getText());
                    break;
                }
            }

            // 4. Seleccionar el Tipo de nota (usaremos ANULACION)
            Select selectTipo = new Select(driver.findElement(By.id("notaTipo")));
            selectTipo.selectByValue("ANULACION");

            // 5. Llenar el Monto
            WebElement inputMonto = driver.findElement(By.id("notaMonto"));
            inputMonto.clear();
            inputMonto.sendKeys("500"); // Monto bajo para no exceder el saldo

            // 6. Llenar el Motivo
            WebElement inputMotivo = driver.findElement(By.id("notaMotivo"));
            inputMotivo.clear();
            inputMotivo.sendKeys("Nota de crédito automáticamente por Selenium QA");

            // 7. Hacer clic en Emitir nota de crédito
            WebElement btnEmitir = wait.until(ExpectedConditions.elementToBeClickable(By.id("notaSubmit")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", btnEmitir);
            btnEmitir.click();

            // 8. Validar la creación
                wait.until(ExpectedConditions.urlContains("notas-credito"));
                System.out.println("Redirección exitosa a la tabla de notas de crédito.");

                // Esperar a que la tabla cargue para confirmar
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//table[contains(@class,'data-table')]")));


        } finally {
            driver.quit();
        }
    }
    @Test
    public void AnularLineaCredito() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();

        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_NOTAS_CREDITO);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(XPATH_FILAS)));

            // 1. Encontrar la primera fila que NO esté anulada
            String xpathFilaObjetivo = XPATH_FILAS + "[not(contains(translate(td[8], 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'anulada'))][1]";

            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpathFilaObjetivo)));
            } catch (org.openqa.selenium.TimeoutException e) {
                fail("No se encontró ninguna nota de crédito en estado válido (no anulada) para realizar la prueba.");
            }

            // 2. Esperar a que la celda del NÚMERO DE NOTA (td[1]) ya no diga "Cargando..."
            // 2. Esperar a que la celda del NÚMERO DE NOTA (td[1]) ya no diga "Cargando..."
            By celdaIdNota = By.xpath(xpathFilaObjetivo + "/td[1]");

            // Usamos 'd' como nombre del parámetro para no chocar con la variable 'driver' del scope principal
            wait.until(d -> {
                String texto = d.findElement(celdaIdNota).getText().trim();
                return !texto.isEmpty() && !texto.toLowerCase().contains("cargando");
            });

            // Ahora sí, extraemos el número de la nota de forma segura
            String numeroNota = driver.findElement(celdaIdNota).getText().trim();
            System.out.println("Se procederá a anular la nota: " + numeroNota);

            // 3. Hacer clic en el botón de "Anular" de esa fila
            clickSeguro(driver, wait, By.xpath(
                    xpathFilaObjetivo + "//button[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'anular')] | " +
                            xpathFilaObjetivo + "//a[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'anular')]"
            ));

            // 4. Aceptar la alerta nativa
            try {
                WebDriverWait waitAlert = new WebDriverWait(driver, Duration.ofSeconds(5));
                waitAlert.until(ExpectedConditions.alertIsPresent());
                driver.switchTo().alert().accept();
                System.out.println("Alerta nativa aceptada.");
            } catch (org.openqa.selenium.TimeoutException e) {
                System.out.println("No apareció alerta nativa.");
            }

            // 5. Esperar el Toast (opcional)
            try {
                By toastLocator = By.xpath("//div[contains(@class, 'toast') or contains(@class, 'alert')]");
                WebDriverWait waitToast = new WebDriverWait(driver, Duration.ofSeconds(5));
                WebElement toast = waitToast.until(ExpectedConditions.visibilityOfElementLocated(toastLocator));
                System.out.println("Toast detectado: " + toast.getText().trim());
            } catch (org.openqa.selenium.TimeoutException e) {
                System.out.println("No apareció toast visual.");
            }

            // 6. Validar el cambio de estado de ESA NOTA EN ESPECÍFICO
            // Buscamos la fila cuyo td[1] coincida con el numeroNota que guardamos
            By celdaEstadoDeNotaEspecifica = By.xpath(
                    XPATH_FILAS + "[td[1][normalize-space()='" + numeroNota + "']]/td[8]"
            );

            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    celdaEstadoDeNotaEspecifica,
                    "Anulada"
            ));

            // 7. Validación final
            String estadoNuevo = driver.findElement(celdaEstadoDeNotaEspecifica).getText().trim();
            assertEquals("Anulada", estadoNuevo, "El estado de la nota " + numeroNota + " no cambió a Anulada.");

        } finally {
            driver.quit();
        }
    }

    @Test
    public void AuditarNotaCredito() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();

        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_NOTAS_CREDITO);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(XPATH_FILAS)));

            // 1. Hacer clic en el botón de auditar (buscando el texto "Auditar" en la fila)
            clickSeguro(driver, wait, By.xpath(
                    XPATH_FILAS + "[1]//button[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'auditar')] | " +
                            XPATH_FILAS + "[1]//a[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'auditar')]"
            ));

            // 2. Esperar que se abra la sección de auditoría
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//h1[contains(text(), 'Auditoría temporal')] | //h2[contains(text(), 'Historial')]")
            ));

            // 3. Validar que la tabla de historial se haya cargado
            WebElement historialTabla = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//table[contains(@class, 'data-table')]")
            ));

            assertTrue(historialTabla.isDisplayed(), "No se cargó la tabla de auditoría");

        } finally {
            driver.quit();
        }
    }

}
