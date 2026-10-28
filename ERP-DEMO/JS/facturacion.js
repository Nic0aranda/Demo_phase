// Lógica para el Modal de Factura
        const invoiceModal = document.getElementById('invoiceModal');
        const viewDetailBtns = document.querySelectorAll('.btn-ver-detalle');
        const closeInvoiceBtn = document.getElementById('closeInvoiceBtn');

        // Abrir modal al hacer clic en "Ver detalle"
        viewDetailBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                invoiceModal.classList.add('active');
            });
        });

        // Cerrar modal
        const closeModal = () => {
            invoiceModal.classList.remove('active');
        };

        closeInvoiceBtn.addEventListener('click', closeModal);

        // Cerrar al hacer clic fuera del modal
        window.addEventListener('click', (e) => {
            if (e.target === invoiceModal) {
                closeModal();
            }
        });