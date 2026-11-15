package Tests.ClientesTesting;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.Select;
import java.util.List;
import org.openqa.selenium.Alert;

import java.time.Duration;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.assertTrue;

import Tests.LoginTesting.LoginHelper;

public class ClientesTest {

    @Test
    public void AccesoAClientes(){

        WebDriver driver = new ChromeDriver();

        try{
            LoginHelper.iniciarSesion(driver);

            WebElement botonClientes = driver.findElement(
                    By.xpath("/html/body/div[1]/aside/nav/ul/li[2]/a")
            );

            // Hacer clic en el botón
            botonClientes.click();

            WebDriverWait wait = new WebDriverWait(
                    driver,
                    Duration.ofSeconds(5)
            );

            // utilizar la instancia de wait para que el driver espere hasta que se cumpla el expected conditions como minimo y 10 segundos como maximo
            boolean llegoAClientes = wait.until(
                    ExpectedConditions.titleContains("Clientes")
            );

            assertTrue(
                    llegoAClientes,
                    "No se pudo acceder a Clientes"
            );

        }
        finally {
            // Cerrar el navegador
            driver.quit();
        }

    }

    // Funcion Util para llegar a la pagina clientes
    public void PaginaClientes(WebDriver driver){

        LoginHelper.iniciarSesion(driver);

        WebElement botonClientes = driver.findElement(
                By.xpath("/html/body/div[1]/aside/nav/ul/li[2]/a")
        );

        // Hacer clic en el botón
        botonClientes.click();

        WebDriverWait wait = new WebDriverWait(
                driver,
                Duration.ofSeconds(5)
        );

        // utilizar la instancia de wait para que el driver espere hasta que se cumpla el expected conditions como minimo y 10 segundos como maximo
        boolean llegoAClientes = wait.until(
                ExpectedConditions.titleContains("Clientes")
        );

        assertTrue(
                llegoAClientes,
                "No se pudo acceder a Clientes"
        );

    }

    // funcion para rellenar formularios mas rapido
    public void RellenarFormulario(WebDriver driver, String XpathFuncion, String Texto){
        WebElement formulario = driver.findElement(By.xpath(XpathFuncion));
        formulario.clear();
        formulario.sendKeys(Texto);
    }

    public void PresionarBoton (WebDriver driver, String XpathFuncion){
        WebElement Boton = driver.findElement(By.xpath(XpathFuncion));
        Boton.click();
    }

    @Test
    public void CrearClienteFunciona(){

        WebDriver driver = new ChromeDriver();

        try {

            PaginaClientes(driver);

            PresionarBoton(driver, "/html/body/div[1]/main/header/div[2]/a");


            WebDriverWait wait = new WebDriverWait(
                    driver,
                    Duration.ofSeconds(5)
            );

            boolean llegoACrearClientes = wait.until(
                    ExpectedConditions.titleContains("Nuevo cliente")
            );

            assertTrue(llegoACrearClientes, "Driver no accedio a Crear Clientes");

            //Seccion 1
            // Rellenar Nombre
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[1]/input",
                    "Prueba CL02" );

            // Rellenar RUT
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[4]/input",
                    "12345678-9");

            // Menu Dropdown del formulario
            WebElement dropdown1 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[3]/select")
            );
            Select opcionDropdown1 = new Select(dropdown1);
            opcionDropdown1.selectByVisibleText("RUT");

            // Seccion 2
            // Elegir Pais
            WebElement dropdown2 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[1]/select")
            );
            Select opcionDropdown2 = new Select(dropdown2);
            opcionDropdown2.selectByVisibleText("Chile");

            //Rellenar Region
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[2]/input",
                    "Metropolitana");

            //Rellenar Direccion
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[3]/input",
                    "Av. Providencia 1234");

            //Seccion 3
            //Rellenar Contacto
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[3]/div[2]/div/input",
                    "PruebaCL02@empresa.com");

            //Boton Crear Cliente
            PresionarBoton(driver,"/html/body/div[1]/main/section/form/div[4]/button");

            // mensaje de error
            WebElement mensajeError = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[4]")
            );

            //en caso de ya haber corrido la prueba
            if(mensajeError.isDisplayed()){
                driver.navigate().back();
            }

            boolean volverAClientes = wait.until(
                    ExpectedConditions.titleContains("Clientes")
            );

            assertTrue(volverAClientes, "Driver no volvio a Clientes");

            //manejar el buscador de clientes
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/header/div[2]/div/input",
                    "Prueba CL02");


            boolean clienteEncontrado = wait.until(
                    ExpectedConditions.textToBePresentInElementLocated(
                            By.xpath("/html/body/div[1]/main/section[2]/table/tbody"),
                            "Prueba CL02"
                    )
            );

            assertTrue(
                    clienteEncontrado,
                    "No se encontró Prueba CL02 dentro de la tabla"
            );


        }
        finally {
            driver.quit();
        }

    }

    @Test
    public void CrearClienteSinNombre() {

        WebDriver driver = new ChromeDriver();

        try {

            PaginaClientes(driver);

            PresionarBoton(driver, "/html/body/div[1]/main/header/div[2]/a");


            WebDriverWait wait = new WebDriverWait(
                    driver,
                    Duration.ofSeconds(5)
            );

            boolean llegoACrearClientes = wait.until(
                    ExpectedConditions.titleContains("Nuevo cliente")
            );

            assertTrue(llegoACrearClientes, "Driver no accedio a Crear Clientes");

            //Seccion 1

            // Rellenar RUT
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[4]/input",
                    "12345678-9");

            // Menu Dropdown del formulario
            WebElement dropdown1 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[3]/select")
            );
            Select opcionDropdown1 = new Select(dropdown1);
            opcionDropdown1.selectByVisibleText("RUT");

            // Seccion 2
            // Elegir Pais
            WebElement dropdown2 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[1]/select")
            );
            Select opcionDropdown2 = new Select(dropdown2);
            opcionDropdown2.selectByVisibleText("Chile");

            //Rellenar Region
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[2]/input",
                    "Metropolitana");

            //Rellenar Direccion
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[3]/input",
                    "Av. Providencia 1234");

            //Seccion 3
            //Rellenar Contacto
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[3]/div[2]/div/input",
                    "PruebaCL02@empresa.com");

            //Boton Crear Cliente
            PresionarBoton(driver, "/html/body/div[1]/main/section/form/div[4]/button");

            // Revisar mensaje de error
            WebElement mensajeError = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[1]/input")
            );

            assertTrue(
                    mensajeError.isDisplayed(),
                    "No apareció el mensaje de error debajo del formulario"
            );


        } finally {
            driver.quit();
        }
    }

    @Test
    public void CrearClienteSinDocumento() {

        WebDriver driver = new ChromeDriver();

        try {

            PaginaClientes(driver);

            PresionarBoton(driver, "/html/body/div[1]/main/header/div[2]/a");


            WebDriverWait wait = new WebDriverWait(
                    driver,
                    Duration.ofSeconds(5)
            );

            boolean llegoACrearClientes = wait.until(
                    ExpectedConditions.titleContains("Nuevo cliente")
            );

            assertTrue(llegoACrearClientes, "Driver no accedio a Crear Clientes");

            //Seccion 1
            //Rellenar Nombre
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[1]/input",
                    "Prueba CL04" );


            // Menu Dropdown del formulario
            WebElement dropdown1 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[3]/select")
            );
            Select opcionDropdown1 = new Select(dropdown1);
            opcionDropdown1.selectByVisibleText("RUT");

            // Seccion 2
            // Elegir Pais
            WebElement dropdown2 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[1]/select")
            );
            Select opcionDropdown2 = new Select(dropdown2);
            opcionDropdown2.selectByVisibleText("Chile");

            //Rellenar Region
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[2]/input",
                    "Metropolitana");

            //Rellenar Direccion
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[3]/input",
                    "Av. Providencia 1234");

            //Seccion 3
            //Rellenar Contacto
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[3]/div[2]/div/input",
                    "PruebaCL02@empresa.com");

            //Boton Crear Cliente
            PresionarBoton(driver, "/html/body/div[1]/main/section/form/div[4]/button");

            // Revisar mensaje de error
            WebElement mensajeError = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[4]/span[1]")
            );

            assertTrue(
                    mensajeError.isDisplayed(),
                    "No apareció el mensaje de error debajo del formulario"
            );


        } finally {
            driver.quit();
        }
    }

    @Test
    public void CrearClienteSinRegion() {

        WebDriver driver = new ChromeDriver();

        try {

            PaginaClientes(driver);

            PresionarBoton(driver, "/html/body/div[1]/main/header/div[2]/a");


            WebDriverWait wait = new WebDriverWait(
                    driver,
                    Duration.ofSeconds(5)
            );

            boolean llegoACrearClientes = wait.until(
                    ExpectedConditions.titleContains("Nuevo cliente")
            );

            assertTrue(llegoACrearClientes, "Driver no accedio a Crear Clientes");

            //Seccion 1
            //Rellenar Nombre
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[1]/input",
                    "Prueba CL04" );

            // Rellenar RUT
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[4]/input",
                    "12345678-9");

            // Menu Dropdown del formulario
            WebElement dropdown1 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[3]/select")
            );
            Select opcionDropdown1 = new Select(dropdown1);
            opcionDropdown1.selectByVisibleText("RUT");

            // Seccion 2
            // Elegir Pais
            WebElement dropdown2 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[1]/select")
            );
            Select opcionDropdown2 = new Select(dropdown2);
            opcionDropdown2.selectByVisibleText("Chile");

            //Rellenar Direccion
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[3]/input",
                    "Av. Providencia 1234");

            //Seccion 3
            //Rellenar Contacto
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[3]/div[2]/div/input",
                    "PruebaCL02@empresa.com");

            //Boton Crear Cliente
            PresionarBoton(driver, "/html/body/div[1]/main/section/form/div[4]/button");

            // Revisar mensaje de error
            WebElement mensajeError = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[2]/input")
            );

            assertTrue(
                    mensajeError.isDisplayed(),
                    "No apareció el mensaje de error debajo del formulario"
            );


        } finally {
            driver.quit();
        }
    }

    @Test
    public void CrearClienteSinDireccion() {

        WebDriver driver = new ChromeDriver();

        try {

            PaginaClientes(driver);

            PresionarBoton(driver, "/html/body/div[1]/main/header/div[2]/a");


            WebDriverWait wait = new WebDriverWait(
                    driver,
                    Duration.ofSeconds(5)
            );

            boolean llegoACrearClientes = wait.until(
                    ExpectedConditions.titleContains("Nuevo cliente")
            );

            assertTrue(llegoACrearClientes, "Driver no accedio a Crear Clientes");

            //Seccion 1
            //Rellenar Nombre
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[1]/input",
                    "Prueba CL04" );

            // Rellenar RUT
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[4]/input",
                    "12345678-9");

            // Menu Dropdown del formulario
            WebElement dropdown1 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[3]/select")
            );
            Select opcionDropdown1 = new Select(dropdown1);
            opcionDropdown1.selectByVisibleText("RUT");

            // Seccion 2
            // Elegir Pais
            WebElement dropdown2 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[1]/select")
            );
            Select opcionDropdown2 = new Select(dropdown2);
            opcionDropdown2.selectByVisibleText("Chile");

            //Rellenar Region
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[2]/input",
                    "Metropolitana");


            //Seccion 3
            //Rellenar Contacto
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[3]/div[2]/div/input",
                    "PruebaCL02@empresa.com");

            //Boton Crear Cliente
            PresionarBoton(driver, "/html/body/div[1]/main/section/form/div[4]/button");

            // Revisar mensaje de error
            WebElement mensajeError = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[3]/span")
            );

            assertTrue(
                    mensajeError.isDisplayed(),
                    "No apareció el mensaje de error debajo del formulario"
            );


        } finally {
            driver.quit();
        }
    }

    @Test
    public void CrearClienteSinEmail() {

        WebDriver driver = new ChromeDriver();

        try {

            PaginaClientes(driver);

            PresionarBoton(driver, "/html/body/div[1]/main/header/div[2]/a");


            WebDriverWait wait = new WebDriverWait(
                    driver,
                    Duration.ofSeconds(5)
            );

            boolean llegoACrearClientes = wait.until(
                    ExpectedConditions.titleContains("Nuevo cliente")
            );

            assertTrue(llegoACrearClientes, "Driver no accedio a Crear Clientes");

            //Seccion 1
            //Rellenar Nombre
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[1]/input",
                    "Prueba CL04" );

            // Rellenar RUT
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[4]/input",
                    "12345678-9");

            // Menu Dropdown del formulario
            WebElement dropdown1 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[3]/select")
            );
            Select opcionDropdown1 = new Select(dropdown1);
            opcionDropdown1.selectByVisibleText("RUT");

            // Seccion 2
            // Elegir Pais
            WebElement dropdown2 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[1]/select")
            );
            Select opcionDropdown2 = new Select(dropdown2);
            opcionDropdown2.selectByVisibleText("Chile");

            //Rellenar Region
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[2]/input",
                    "Metropolitana");

            //Rellenar Direccion
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[3]/input",
                    "Av. Providencia 1234");

            //Seccion 3


            //Boton Crear Cliente
            PresionarBoton(driver, "/html/body/div[1]/main/section/form/div[4]/button");

            // Revisar mensaje de error
            WebElement mensajeError = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[3]/div[2]/div/span")
            );

            assertTrue(
                    mensajeError.isDisplayed(),
                    "No apareció el mensaje de error debajo del formulario"
            );


        } finally {
            driver.quit();
        }
    }

    @Test
    public void EditarClienteFunciona(){

        WebDriver driver = new ChromeDriver();

        try {

            PaginaClientes(driver);

            PresionarBoton(driver, "/html/body/div[1]/main/header/div[2]/a");


            WebDriverWait wait = new WebDriverWait(
                    driver,
                    Duration.ofSeconds(5)
            );

            boolean llegoACrearClientes = wait.until(
                    ExpectedConditions.titleContains("Nuevo cliente")
            );

            assertTrue(llegoACrearClientes, "Driver no accedio a Crear Clientes");

            //Seccion 1
            // Rellenar Nombre
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[1]/input",
                    "Prueba CL08" );

            // Rellenar RUT
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[4]/input",
                    "21345678-9");

            // Menu Dropdown del formulario
            WebElement dropdown1 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[3]/select")
            );
            Select opcionDropdown1 = new Select(dropdown1);
            opcionDropdown1.selectByVisibleText("RUT");

            // Seccion 2
            // Elegir Pais
            WebElement dropdown2 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[1]/select")
            );
            Select opcionDropdown2 = new Select(dropdown2);
            opcionDropdown2.selectByVisibleText("Chile");

            //Rellenar Region
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[2]/input",
                    "RegionInicial");

            //Rellenar Direccion
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[3]/input",
                    "Av. Providencia 1234");

            //Seccion 3
            //Rellenar Contacto
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[3]/div[2]/div/input",
                    "PruebaCL02@empresa.com");

            //Boton Crear Cliente
            PresionarBoton(driver,"/html/body/div[1]/main/section/form/div[4]/button");

            // mensaje de error
            WebElement mensajeError = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[4]")
            );

            //en caso de ya haber corrido la prueba
            if(mensajeError.isDisplayed()){
                driver.navigate().back();
            }

            boolean volverAClientes = wait.until(
                    ExpectedConditions.titleContains("Clientes")
            );

            assertTrue(volverAClientes, "Driver no volvio a Clientes");

            //actualizar los datos de Clientes
            driver.navigate().refresh();

            //manejar el buscador de clientes
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/header/div[2]/div/input",
                    "Prueba CL08");


            boolean clienteEncontrado = wait.until(
                    ExpectedConditions.textToBePresentInElementLocated(
                            By.xpath("/html/body/div[1]/main/section[2]/table/tbody"),
                            "Prueba CL08"
                    )
            );

            assertTrue(
                    clienteEncontrado,
                    "No se encontró Prueba CL08 dentro de la tabla"
            );

            //presionar boton de editar
            PresionarBoton(driver, "/html/body/div[1]/main/section[2]/table/tbody/tr/td[6]/a");

            //Esperar a llegar a editar
            boolean llegoAEditar = wait.until(
                    ExpectedConditions.titleContains("Nuevo cliente")
            );

            assertTrue(
                    llegoAEditar,
                    "No se encontró Prueba CL08 dentro de la tabla"
            );


            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[2]/input",
                    "RegionEditada");

            //Presionar boton de guardar cambios
            PresionarBoton(driver, "/html/body/div[1]/main/section/form/div[4]/button");

            //volvemos a la pagina Clientes
            //Esperar a llegar a editar
            boolean volverAClientes2 = wait.until(
                    ExpectedConditions.titleContains("Clientes")
            );

            //actualizar los datos de Clientes
            driver.navigate().refresh();

            //manejar el buscador de clientes
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/header/div[2]/div/input",
                    "Prueba CL08");

            boolean regionEditadaEncontrada = wait.until(
                    ExpectedConditions.textToBePresentInElementLocated(
                            By.xpath("/html/body/div[1]/main/section[2]/table/tbody"),
                            "RegionEditada"
                    )
            );

            assertTrue(
                    regionEditadaEncontrada,
                    "No se encontró la RegionEditada"
            );


        }
        finally {
            driver.quit();
        }

    }

    @Test
    public void DesactivarClienteFunciona(){

        WebDriver driver = new ChromeDriver();

        try {

            PaginaClientes(driver);

            PresionarBoton(driver, "/html/body/div[1]/main/header/div[2]/a");


            WebDriverWait wait = new WebDriverWait(
                    driver,
                    Duration.ofSeconds(5)
            );

            boolean llegoACrearClientes = wait.until(
                    ExpectedConditions.titleContains("Nuevo cliente")
            );

            assertTrue(llegoACrearClientes, "Driver no accedio a Crear Clientes");

            //Seccion 1
            // Rellenar Nombre
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[1]/input",
                    "Prueba CL09" );

            // Rellenar RUT
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[4]/input",
                    "32145678-9");

            // Menu Dropdown del formulario
            WebElement dropdown1 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[1]/div[2]/div/div[3]/select")
            );
            Select opcionDropdown1 = new Select(dropdown1);
            opcionDropdown1.selectByVisibleText("RUT");

            // Seccion 2
            // Elegir Pais
            WebElement dropdown2 = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[1]/select")
            );
            Select opcionDropdown2 = new Select(dropdown2);
            opcionDropdown2.selectByVisibleText("Chile");

            //Rellenar Region
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[2]/input",
                    "Los Rios");

            //Rellenar Direccion
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[2]/div[2]/div/div[3]/input",
                    "Av. Providencia 4321");

            //Seccion 3
            //Rellenar Contacto
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/section/form/div[3]/div[2]/div/input",
                    "PruebaCL09@empresa.com");

            //Boton Crear Cliente
            PresionarBoton(driver,"/html/body/div[1]/main/section/form/div[4]/button");

            // mensaje de error
            WebElement mensajeError = driver.findElement(
                    By.xpath("/html/body/div[1]/main/section/form/div[4]")
            );

            //en caso de ya haber corrido la prueba
            if(mensajeError.isDisplayed()){
                driver.navigate().back();
            }

            boolean volverAClientes = wait.until(
                    ExpectedConditions.titleContains("Clientes")
            );

            assertTrue(volverAClientes, "Driver no volvio a Clientes");

            // actualizamos los datos de clientes
            driver.navigate().refresh();

            //manejar el buscador de clientes
            RellenarFormulario(driver,
                    "/html/body/div[1]/main/header/div[2]/div/input",
                    "Prueba CL09");




            boolean clienteEncontrado = wait.until(
                    ExpectedConditions.textToBePresentInElementLocated(
                            By.xpath("/html/body/div[1]/main/section[2]/table/tbody"),
                            "Prueba CL09"
                    )
            );

            assertTrue(
                    clienteEncontrado,
                    "No se encontró Prueba CL09 dentro de la tabla"
            );

            //presionamos el boton de Desactivar cliente
            PresionarBoton(driver, "/html/body/div[1]/main/section[2]/table/tbody/tr/td[6]/button");

            //se espera a que aparezca el popup
            Alert alerta = wait.until(
                    ExpectedConditions.alertIsPresent()
            );

            // Aceptar el popup
            alerta.accept();

            // path al "activo" /html/body/div[1]/main/section[2]/table/tbody/tr/td[5]
            boolean estadoActualizado = wait.until(
                    ExpectedConditions.textToBe(
                            By.xpath("/html/body/div[1]/main/section[2]/table/tbody/tr/td[5]"),
                            "Inactivo"
                    )
            );

            assertTrue(
                    estadoActualizado,
                    "El cliente no aparece como Inactivo"
            );

        }
        finally {
            driver.quit();
        }

    }




}
