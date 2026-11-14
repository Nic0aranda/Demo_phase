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

public class LoginHelper {

    public static void iniciarSesion(WebDriver driver) {

        // 1. Navegar al Login
        driver.get("http://localhost:5500/Index.html");

        // 2. Configurar espera
        WebDriverWait wait = new WebDriverWait(
                driver,
                Duration.ofSeconds(10)
        );

        // 3. Localizar formulario de email
        WebElement email = wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.xpath("//*[@id='email']")
                )
        );

        // 4. Ingresar email
        email.sendKeys("admin@empresa.com");

        // 5. Localizar formulario de contraseña
        WebElement password = driver.findElement(
                By.xpath("/html/body/div/div[2]/form/div[2]/input")
        );

        // 6. Ingresar contraseña
        password.sendKeys("admin123");

        // 7. Localizar y pulsar botón Login
        WebElement botonLogin = driver.findElement(
                By.xpath("/html/body/div/div[2]/form/button")
        );

        botonLogin.click();

        // 8. Esperar hasta llegar al Dashboard
        wait.until(
                ExpectedConditions.urlContains("dashboard")
        );
    }


}
