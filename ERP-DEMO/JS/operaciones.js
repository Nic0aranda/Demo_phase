// --- LÓGICA DEL MODAL DE DOCUMENTOS ---
        const modal = document.getElementById('docsModal');
        const docBtns = document.querySelectorAll('.doc-btn');
        const closeBtn = document.getElementById('closeModalBtn');
        const cancelBtn = document.getElementById('cancelModalBtn');

        // Abrir modal al hacer clic en cualquier botón "Documentos"
        docBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                modal.classList.add('active');
            });
        });

        // Función genérica para cerrar modal
        const closeModal = () => {
            modal.classList.remove('active');
        };

        // Cerrar modal al hacer clic en la X o en Cancelar
        closeBtn.addEventListener('click', closeModal);
        cancelBtn.addEventListener('click', closeModal);

        // Cerrar modal al hacer clic fuera de la caja blanca (en el fondo oscuro)
        window.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeModal();
            }
        });