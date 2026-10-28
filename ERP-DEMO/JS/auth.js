const mockDatabase = {
    "admin@empresa.com": {
        roleId: 1,
        name: "Laura Martínez",
        dashboardUrl: "views/admin/dashboard.html"
    },
    "comercial@empresa.com": {
        roleId: 2,
        name: "Diego Sánchez",
        dashboardUrl: "views/comercial/dashboard.html"
    },
    "operaciones@empresa.com": {
        roleId: 3,
        name: "Ana Torres",
        dashboardUrl: "views/operaciones/dashboard.html"
    },
    "contabilidad@empresa.com": {
        roleId: 4,
        name: "Ana Torres (Contabilidad)",
        dashboardUrl: "views/contabilidad/dashboard.html"
    }
};

document.addEventListener('DOMContentLoaded', () => {
    const loginForm = document.getElementById('loginForm');
    const errorMessage = document.getElementById('errorMessage');

    loginForm.addEventListener('submit', function(event) {
        // Evita que la página se recargue (comportamiento por defecto del form)
        event.preventDefault();

        // Obtenemos los valores de los inputs
        const email = document.getElementById('email').value.trim();
        const password = document.getElementById('password').value;

        // Ocultamos el mensaje de error por si estaba visible
        errorMessage.style.display = 'none';

        // 1. Verificamos si el correo existe en nuestra base de datos simulada
        const user = mockDatabase[email];

        // 2. Simulamos la validación de contraseña (para el demo aceptaremos cualquier password como válida)
        if (user && password.length > 0) {
            
            // ÉXITO: Guardamos el rol en el LocalStorage para usarlo en otras páginas
            // Esto es útil para que el Dashboard sepa quién está logueado
            localStorage.setItem('userRoleId', user.roleId);
            localStorage.setItem('userName', user.name);

            // Redirigimos al usuario a la URL que corresponde a su rol
            window.location.href = user.dashboardUrl;

        } else {
            // ERROR: Correo no existe o contraseña vacía
            errorMessage.textContent = 'Usuario no encontrado. Prueba con admin@empresa.com';
            errorMessage.style.display = 'block';
        }
    });
});