package Tests.UsuariosTesting;

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

public class UsuarioTest {

    private static final String URL_USUARIOS = "http://localhost:5500/views/admin/users.html";
    private static final String XPATH_TABLA = "//table[contains(@class,'data-table')]";
    private static final String XPATH_FILAS = "//tbody[@id='usuariosTableBody']/tr[not(contains(@class,'table-message'))]";
    private static final String XPATH_CELDAS_ROL = XPATH_FILAS + "/td[2]";
    private static final String XPATH_CELDAS_ESTADO = XPATH_FILAS + "/td[3]";

    private void clickSeguro(WebDriver driver, WebDriverWait wait, By locator) {
        WebElement elemento = wait.until(ExpectedConditions.elementToBeClickable(locator));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", elemento);
        elemento.click();
    }

    // US-01 — Ver la lista de usuarios
    @Test
    public void VisualizarListaUsuarios() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_USUARIOS);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(XPATH_FILAS)));

            List<WebElement> filas = driver.findElements(By.xpath(XPATH_FILAS));
            assertFalse(filas.isEmpty(), "La tabla de usuarios no muestra registros");

        } finally {
            driver.quit();
        }
    }

    // US-02 — Filtrar por Rol
    @Test
    public void FiltrarUsuariosPorRol() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_USUARIOS);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(XPATH_FILAS)));

            new Select(driver.findElement(By.id("usuarioRol"))).selectByValue("admin");

            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath(XPATH_FILAS + "[td[2][contains(normalize-space(), 'Administrador')]]")
            ));

            List<WebElement> celdasRol = driver.findElements(By.xpath(XPATH_CELDAS_ROL));
            assertFalse(celdasRol.isEmpty(), "El filtro por rol no devolvió resultados");

            for (WebElement celda : celdasRol) {
                assertEquals("Administrador", celda.getText().trim(), "Apareció un usuario con un rol distinto al filtrado");
            }
        } finally {
            driver.quit();
        }
    }

    // US-03 — Filtrar por Estado
    @Test
    public void FiltrarUsuariosPorEstado() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_USUARIOS);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(XPATH_FILAS)));

            new Select(driver.findElement(By.id("usuarioEstado"))).selectByValue("activo");

            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath(XPATH_FILAS + "[td[3][contains(normalize-space(), 'Activo')]]")
            ));

            List<WebElement> celdasEstado = driver.findElements(By.xpath(XPATH_CELDAS_ESTADO));
            assertFalse(celdasEstado.isEmpty(), "El filtro por estado no devolvió resultados");

            for (WebElement celda : celdasEstado) {
                assertEquals("Activo", celda.getText().trim(), "Apareció un usuario con un estado distinto al filtrado");
            }
        } finally {
            driver.quit();
        }
    }

    // US-04 — Recalcular estados (Confirmación + Toast)
    @Test
    public void RecalcularEstadosUsuarios() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_USUARIOS);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));

            clickSeguro(driver, wait, By.id("recalcularBtn"));

            try {
                WebDriverWait waitAlert = new WebDriverWait(driver, Duration.ofSeconds(5));
                waitAlert.until(ExpectedConditions.alertIsPresent());
                driver.switchTo().alert().accept();
                System.out.println("Confirmación nativa aceptada.");
            } catch (org.openqa.selenium.TimeoutException e) {
                System.out.println("No apareció confirmación nativa.");
            }

            try {
                By toastLocator = By.xpath("//div[contains(@class, 'toast') or contains(@class, 'alert')]");
                WebElement toast = wait.until(ExpectedConditions.visibilityOfElementLocated(toastLocator));
                String textoToast = toast.getText().trim();
                System.out.println("Toast detectado: " + textoToast);
                assertFalse(textoToast.isEmpty(), "El toast de resultado está vacío");
            } catch (org.openqa.selenium.TimeoutException e) {
                fail("No apareció el toast de resultado tras recalcular estados.");
            }

            assertTrue(driver.getCurrentUrl().contains("users.html"), "La página no debió redirigir tras recalcular.");

        } finally {
            driver.quit();
        }
    }

    // US-05 — Desactivar una cuenta (Blindado contra Administradores)
    @Test
    public void DesactivarCuentaUsuario() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_USUARIOS);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(XPATH_FILAS)));

            // 1. Buscar una fila que esté "Activo" y que NO sea "Administrador"
            // td[2] es ROL, td[3] es ESTADO
            String xpathFilaObjetivo = XPATH_FILAS +
                    "[td[3][contains(normalize-space(), 'Activo')] and not(contains(td[2], 'Administrador'))][1]";

            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpathFilaObjetivo)));
            } catch (org.openqa.selenium.TimeoutException e) {
                fail("No se encontró ningún usuario Activo que NO sea Administrador para desactivar. " +
                        "Por favor, crea un usuario de otro rol para continuar con el test.");
            }

            // 2. Obtener el nombre del usuario (solo el nombre, no el email)
            WebElement fila = driver.findElement(By.xpath(xpathFilaObjetivo));
            String textoCompleto = fila.findElement(By.xpath("./td[1]")).getText();
            String nombreUsuario = textoCompleto.split("\n")[0].trim(); // Solo la primera línea
            System.out.println("Desactivando al usuario: " + nombreUsuario);

            // 3. Clic en desactivar
            clickSeguro(driver, wait, By.xpath(
                    xpathFilaObjetivo + "//button[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'desactivar')] | " +
                            xpathFilaObjetivo + "//a[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'desactivar')]"
            ));

            // 4. Aceptar alerta nativa si existe
            try {
                WebDriverWait waitAlert = new WebDriverWait(driver, Duration.ofSeconds(3));
                waitAlert.until(ExpectedConditions.alertIsPresent());
                driver.switchTo().alert().accept();
            } catch (org.openqa.selenium.TimeoutException e) { /* Ignorar */ }

            // 5. Esperar que la tabla se actualice y el usuario pase a "Inactivo"
            // Buscamos la fila por el nombre (usando contains por si el email sigue en el texto)
            By celdaEstadoUsuario = By.xpath(
                    XPATH_FILAS + "[td[1][contains(normalize-space(), '" + nombreUsuario + "')]]/td[3]"
            );
            wait.until(ExpectedConditions.textToBePresentInElementLocated(celdaEstadoUsuario, "Inactivo"));

            String estadoNuevo = driver.findElement(celdaEstadoUsuario).getText().trim();
            assertTrue(estadoNuevo.contains("Inactivo"), "El usuario no cambió a estado Inactivo.");

        } finally {
            driver.quit();
        }
    }
    // US-06 — Activar una cuenta (Test autosuficiente y robusto)
    @Test
    public void ActivarCuentaUsuario() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_USUARIOS);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));

            // ESPERA CLAVE: Asegurarnos de que la tabla ya no muestra "Cargando usuarios…"
            wait.until(d -> {
                List<WebElement> filas = d.findElements(By.xpath(XPATH_FILAS));
                if (filas.isEmpty()) return false;
                // Si la primera fila contiene "Cargando", aún no está lista
                String primeraCelda = filas.get(0).findElement(By.xpath("./td[1]")).getText().trim();
                return !primeraCelda.contains("Cargando") && !primeraCelda.isEmpty();
            });

            // PASO 1: Verificar si ya hay un usuario inactivo (que no sea Administrador)
            String xpathFilaInactiva = XPATH_FILAS +
                    "[td[3][contains(normalize-space(), 'Inactivo')] and not(contains(td[2], 'Administrador'))][1]";

            boolean hayInactivo = !driver.findElements(By.xpath(xpathFilaInactiva)).isEmpty();

            // Si no hay inactivos, desactivamos uno (que no sea Administrador)
            if (!hayInactivo) {
                System.out.println("No hay usuarios inactivos. Desactivando uno para la prueba...");

                String xpathFilaActiva = XPATH_FILAS +
                        "[td[3][contains(normalize-space(), 'Activo')] and not(contains(td[2], 'Administrador'))][1]";

                try {
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpathFilaActiva)));
                } catch (org.openqa.selenium.TimeoutException e) {
                    fail("No hay usuarios Activos que no sean Administrador para preparar el test de activación.");
                }

                WebElement filaActiva = driver.findElement(By.xpath(xpathFilaActiva));
                String nombreAux = filaActiva.findElement(By.xpath("./td[1]")).getText().split("\n")[0].trim();

                clickSeguro(driver, wait, By.xpath(
                        xpathFilaActiva + "//button[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'desactivar')] | " +
                                xpathFilaActiva + "//a[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'desactivar')]"
                ));

                try {
                    WebDriverWait waitAlert = new WebDriverWait(driver, Duration.ofSeconds(3));
                    waitAlert.until(ExpectedConditions.alertIsPresent());
                    driver.switchTo().alert().accept();
                } catch (org.openqa.selenium.TimeoutException e) { /* Ignorar */ }

                // Esperar a que el cambio se refleje en el DOM
                wait.until(ExpectedConditions.textToBePresentInElementLocated(
                        By.xpath(XPATH_FILAS + "[td[1][contains(normalize-space(), '" + nombreAux + "')]]/td[3]"),
                        "Inactivo"
                ));
            }

            // PASO 2: Ahora sí, buscar el inactivo y activarlo
            // (Puede ser el que acabamos de desactivar o uno que ya existía)
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpathFilaInactiva)));

            WebElement filaInactiva = driver.findElement(By.xpath(xpathFilaInactiva));
            String textoCompleto = filaInactiva.findElement(By.xpath("./td[1]")).getText();
            String nombreUsuario = textoCompleto.split("\n")[0].trim();
            System.out.println("Activando al usuario: " + nombreUsuario);

            clickSeguro(driver, wait, By.xpath(
                    xpathFilaInactiva + "//button[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'activar')] | " +
                            xpathFilaInactiva + "//a[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'activar')]"
            ));

            try {
                WebDriverWait waitAlert = new WebDriverWait(driver, Duration.ofSeconds(3));
                waitAlert.until(ExpectedConditions.alertIsPresent());
                driver.switchTo().alert().accept();
            } catch (org.openqa.selenium.TimeoutException e) { /* Ignorar */ }

            // Validar cambio a Activo
            By celdaEstadoUsuario = By.xpath(
                    XPATH_FILAS + "[td[1][contains(normalize-space(), '" + nombreUsuario + "')]]/td[3]"
            );
            wait.until(ExpectedConditions.textToBePresentInElementLocated(celdaEstadoUsuario, "Activo"));

            String estadoNuevo = driver.findElement(celdaEstadoUsuario).getText().trim();
            assertTrue(estadoNuevo.contains("Activo"), "El usuario no cambió a estado Activo.");

        } finally {
            driver.quit();
        }
    }
}