package Tests.CuentasPorCobrarTesting;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

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

    @Test
    public void VisualizarTablaCuentasPorCobrar() {

        // Inicializar el navegador
        WebDriver driver = new ChromeDriver();

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

}