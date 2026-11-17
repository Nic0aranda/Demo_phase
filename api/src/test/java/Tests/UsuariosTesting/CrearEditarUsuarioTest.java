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

import static org.junit.jupiter.api.Assertions.assertTrue;

import Tests.LoginTesting.LoginHelper;

public class CrearEditarUsuarioTest {

    private static final String URL_USUARIOS = "http://localhost:5500/views/admin/users.html";
    private static final String URL_CREAR = "http://localhost:5500/views/admin/crear-usuario.html";
    private static final String XPATH_TABLA = "//table[contains(@class,'data-table')]";
    private static final String XPATH_FILAS = "//tbody[@id='usuariosTableBody']/tr[not(contains(@class,'table-message'))]";

    private void clickSeguro(WebDriver driver, WebDriverWait wait, By locator) {
        WebElement elemento = wait.until(ExpectedConditions.elementToBeClickable(locator));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", elemento);
        elemento.click();
    }

    private String generarEmailUnico() {
        return "qa.test." + System.currentTimeMillis() + "@test.local";
    }

    // US-07 — Crear un nuevo usuario
    @Test
    public void CrearNuevoUsuario() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_CREAR);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("usuarioForm")));

            String emailGenerado = generarEmailUnico();

            // Llenar campos
            driver.findElement(By.id("nombres")).sendKeys("Test");
            driver.findElement(By.id("apellidos")).sendKeys("Automatizado");
            driver.findElement(By.id("email")).sendKeys(emailGenerado);

            new Select(driver.findElement(By.id("rol"))).selectByValue("comercial");
            new Select(driver.findElement(By.id("estado"))).selectByValue("activo");

            driver.findElement(By.id("password")).sendKeys("Password123!");
            driver.findElement(By.id("passwordConfirm")).sendKeys("Password123!");

            // Hacer clic en Crear usuario
            clickSeguro(driver, wait, By.id("usuarioSubmit"));

            // Validar que redirige a la lista
            wait.until(ExpectedConditions.urlContains("users.html"));

            // Validar que el nuevo usuario aparece en la tabla (buscando por el email generado)
            By emailEnTabla = By.xpath(XPATH_FILAS + "[contains(., '" + emailGenerado + "')]");
            wait.until(ExpectedConditions.presenceOfElementLocated(emailEnTabla));

            assertTrue(driver.findElement(emailEnTabla).isDisplayed(), "El usuario recién creado no aparece en la tabla.");

        } finally {
            driver.quit();
        }
    }

    // US-08 — Editar un usuario existente
    @Test
    public void EditarUsuarioExistente() {
        WebDriver driver = new ChromeDriver();
        driver.manage().window().maximize();
        try {
            LoginHelper.iniciarSesion(driver);
            driver.get(URL_USUARIOS);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(XPATH_TABLA)));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(XPATH_FILAS)));

            // 1. Hacer clic en el botón "Editar" de la primera fila
            String xpathPrimeraFila = XPATH_FILAS + "[1]";
            String nombreUsuario = driver.findElement(By.xpath(xpathPrimeraFila + "/td[1]")).getText().trim();
            System.out.println("Editando al usuario: " + nombreUsuario);

            clickSeguro(driver, wait, By.xpath(
                    xpathPrimeraFila + "//button[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'editar')] | " +
                            xpathPrimeraFila + "//a[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'editar')]"
            ));

            // 2. Validar que se abre el formulario de edición
            wait.until(ExpectedConditions.urlContains("crear-usuario.html"));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("usuarioForm")));

            // Validar que el título indica que es una edición y que los datos cargaron
            WebElement titulo = driver.findElement(By.id("usuarioFormTitle"));
            assertTrue(titulo.getText().contains("Editar") || titulo.getText().contains("Actualizar"),
                    "El título no indica que es una edición. Se obtuvo: " + titulo.getText());

            // (Opcional) Modificar un campo y guardar
            WebElement inputNombres = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("nombres")));
            inputNombres.clear();
            inputNombres.sendKeys("Test Editado");

            clickSeguro(driver, wait, By.id("usuarioSubmit"));

            // 3. Validar que vuelve a la lista
            wait.until(ExpectedConditions.urlContains("users.html"));

        } finally {
            driver.quit();
        }
    }
}