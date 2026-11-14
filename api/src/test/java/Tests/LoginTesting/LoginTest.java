
package Tests.LoginTesting;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

import java.time.Duration;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoginTest {

    @Test
    public void IniciarSesionFunciona() {

        // Inicializar el navegador
        WebDriver driver = new ChromeDriver();

        try {

            // Navegar a el ERP
            driver.get("http://localhost:5500/Index.html");

            // Buscar el formulario de email
            WebElement email = driver.findElement(
                    By.xpath("//*[@id='email']")
            );

            // Escribir el email
            email.sendKeys("admin@empresa.com");

            // Buscar el formulario de contraseña
            WebElement password = driver.findElement(
                    By.xpath("/html/body/div/div[2]/form/div[2]/input")
            );

            // Escribir la contraseña
            password.sendKeys("admin123");

            // Buscar el botón de Login
            WebElement botonLogin = driver.findElement(
                    By.xpath("/html/body/div/div[2]/form/button")
            );

            // Hacer clic en el botón
            botonLogin.click();

            // Configurar una instancia de "wait" con una tolerancia de hasta 10 segundos
            WebDriverWait wait = new WebDriverWait(
                    driver,
                    Duration.ofSeconds(10)
            );

            // utilizar la instancia de wait para que el driver espere hasta que se cumpla el expected conditions como minimo y 10 segundos como maximo
            boolean llegoAlDashboard = wait.until(
                    ExpectedConditions.urlContains("dashboard")
            );

            // Comprobar el booleano de "llegoAlDashboard" y si es false, entregar mensaje de error
            assertTrue(
                    llegoAlDashboard,
                    "No se pudo acceder al Dashboard"
            );

        } finally {

            // Cerrar el navegador
            driver.quit();

        }
    }
}
