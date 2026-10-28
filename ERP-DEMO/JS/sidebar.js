document.addEventListener('DOMContentLoaded', () => {
    // Buscar el botón de cerrar sesión
    const logoutBtn = document.getElementById('logoutBtn');
    
    // Si el botón existe en la vista actual, le agregamos el evento
    if (logoutBtn) {
        logoutBtn.addEventListener('click', function() {
            // Limpiar la sesión
            localStorage.clear();
            
            // Redirigir al Index. 
            // Como los archivos HTML están dentro de vistas/carpeta/, bajamos dos niveles
            window.location.href = '../../Index.html';
        });
    }
});