package com.phase.api.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.phase.api.common.ApiException;
import com.phase.api.config.FinanceProperties;
import com.phase.api.dto.ClienteDTO;
import com.phase.api.dto.FacturaDTO;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Genera el PDF real de una factura con OpenPDF. El formato usa la moneda
 * configurada en {@code erp.finance.currency}.
 */
@Service
public class FacturaPdfService {

    private static final Color MORADO = new Color(76, 42, 122);
    private static final Color GRIS = new Color(100, 100, 100);
    private static final Color FONDO_SUAVE = new Color(243, 240, 248);

    private static final Font TITULO = new Font(Font.HELVETICA, 18, Font.BOLD, MORADO);
    private static final Font SUBTITULO = new Font(Font.HELVETICA, 10, Font.NORMAL, GRIS);
    private static final Font ETIQUETA = new Font(Font.HELVETICA, 8, Font.BOLD, GRIS);
    private static final Font TEXTO = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font TEXTO_NEGRITA = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font CABECERA_TABLA = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font TOTAL = new Font(Font.HELVETICA, 12, Font.BOLD, MORADO);

    private final FacturaService facturaService;
    private final ClienteService clienteService;
    private final FinanceProperties financeProperties;

    public FacturaPdfService(FacturaService facturaService,
                             ClienteService clienteService,
                             FinanceProperties financeProperties) {
        this.facturaService = facturaService;
        this.clienteService = clienteService;
        this.financeProperties = financeProperties;
    }

    public byte[] generar(String facturaId) {
        FacturaDTO factura = facturaService.obtenerPorId(facturaId);
        ClienteDTO cliente = clienteDe(factura.getRutCliente());

        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(encabezado(factura));
            document.add(separador());
            document.add(datosFactura(factura, cliente));
            document.add(separacion());
            document.add(tablaItems(factura));
            document.add(separacion());
            document.add(totales(factura));
            document.add(separacion());
            document.add(pie());

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            if (document.isOpen()) {
                document.close();
            }
            throw new IllegalStateException("No se pudo generar el PDF de la factura " + facturaId, e);
        }
    }

    private PdfPTable encabezado(FacturaDTO factura) {
        Paragraph titulo = new Paragraph("ERP EMPRESARIAL", TITULO);

        Paragraph derechaTexto = new Paragraph();
        derechaTexto.setAlignment(Element.ALIGN_RIGHT);
        derechaTexto.add(new Phrase("FACTURA DE VENTA\n", TEXTO_NEGRITA));
        derechaTexto.add(new Phrase(factura.getId(), SUBTITULO));

        PdfPTable table = new PdfPTable(new float[]{3, 2});
        table.setWidthPercentage(100);
        table.addCell(celdaSinBorde(titulo));
        table.addCell(celdaSinBorde(derechaTexto));
        return table;
    }

    private PdfPTable separador() {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase(" "));
        cell.setBorderWidthTop(1.5f);
        cell.setBorderWidthBottom(0);
        cell.setBorderWidthLeft(0);
        cell.setBorderWidthRight(0);
        cell.setBorderColor(MORADO);
        table.addCell(cell);
        return table;
    }

    private Paragraph separacion() {
        Paragraph paragraph = new Paragraph(" ");
        paragraph.setSpacingBefore(8);
        paragraph.setSpacingAfter(8);
        return paragraph;
    }

    private PdfPTable datosFactura(FacturaDTO factura, ClienteDTO cliente) {
        PdfPTable table = new PdfPTable(new float[]{1, 1});
        table.setWidthPercentage(100);
        table.getDefaultCell().setBorder(0);
        table.getDefaultCell().setPadding(4);

        table.addCell(dato("CLIENTE",
                cliente == null ? factura.getRutCliente() : cliente.getNombre()));
        table.addCell(dato("RUT", factura.getRutCliente()));
        table.addCell(dato("FECHA DE EMISION", valor(factura.getFechaEmision())));
        table.addCell(dato("FECHA DE VENCIMIENTO", valor(factura.getFechaVencimiento())));
        table.addCell(dato("CONDICION DE PAGO", condicion(factura.getCondicionPago())));
        table.addCell(dato("ESTADO", valor(factura.getEstado())));
        if (cliente != null) {
            table.addCell(dato("DIRECCION", valor(cliente.getDireccion())));
            table.addCell(dato("EMAIL", valor(cliente.getEmail())));
        }
        return table;
    }

    private PdfPCell dato(String etiqueta, String valor) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        cell.setPadding(4);
        Paragraph label = new Paragraph(etiqueta, ETIQUETA);
        label.setSpacingAfter(2);
        cell.addElement(label);
        cell.addElement(new Paragraph(valor, TEXTO));
        return cell;
    }

    private PdfPTable tablaItems(FacturaDTO factura) {
        PdfPTable table = new PdfPTable(new float[]{5, 1, 2, 2});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        table.addCell(cabecera("Descripcion", Element.ALIGN_LEFT));
        table.addCell(cabecera("Cant.", Element.ALIGN_CENTER));
        table.addCell(cabecera("Precio unit.", Element.ALIGN_RIGHT));
        table.addCell(cabecera("Total", Element.ALIGN_RIGHT));

        if (factura.getItems() != null) {
            for (FacturaDTO.ItemFacturaDTO item : factura.getItems()) {
                table.addCell(celda(item.getDescripcion(), Element.ALIGN_LEFT));
                table.addCell(celda(String.valueOf(item.getCantidad()), Element.ALIGN_CENTER));
                table.addCell(celda(moneda(item.getPrecioUnitario()), Element.ALIGN_RIGHT));
                table.addCell(celda(moneda(item.getTotalLinea()), Element.ALIGN_RIGHT));
            }
        }
        return table;
    }

    private PdfPTable totales(FacturaDTO factura) {
        PdfPTable table = new PdfPTable(new float[]{5, 2});
        table.setWidthPercentage(100);
        table.getDefaultCell().setBorder(0);

        table.addCell(celdaSinBorde(" "));
        table.addCell(totalFila("Subtotal", moneda(factura.getSubtotal()), false));
        table.addCell(celdaSinBorde(" "));
        table.addCell(totalFila("Impuestos", moneda(factura.getImpuestos()), false));
        table.addCell(celdaSinBorde(" "));
        table.addCell(totalFila("TOTAL", moneda(factura.getTotal()) + " " + financeProperties.getCurrency(), true));
        return table;
    }

    private PdfPCell totalFila(String etiqueta, String valor, boolean destacado) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        cell.setPadding(5);
        cell.setBackgroundColor(destacado ? FONDO_SUAVE : Color.WHITE);
        Paragraph paragraph = new Paragraph(etiqueta + ":  " + valor, destacado ? TOTAL : TEXTO);
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        cell.addElement(paragraph);
        return cell;
    }

    private Paragraph pie() {
        Paragraph paragraph = new Paragraph(
                "Documento generado por ERP Empresarial el "
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
                        + ". Moneda: " + financeProperties.getCurrency()
                        + ". IVA aplicado: " + financeProperties.getIvaRate().multiply(BigDecimal.valueOf(100)).stripTrailingZeros() + "%.",
                SUBTITULO);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        return paragraph;
    }

    private PdfPCell cabecera(String texto, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, CABECERA_TABLA));
        cell.setBackgroundColor(MORADO);
        cell.setHorizontalAlignment(align);
        cell.setPadding(7);
        return cell;
    }

    private PdfPCell celda(String texto, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(valor(texto), TEXTO));
        cell.setHorizontalAlignment(align);
        cell.setPadding(6);
        cell.setBorderColor(new Color(220, 220, 220));
        return cell;
    }

    private PdfPCell celdaSinBorde(String texto) {
        return celdaSinBorde(new Paragraph(valor(texto), TEXTO));
    }

    private PdfPCell celdaSinBorde(Paragraph paragraph) {
        PdfPCell cell = new PdfPCell(paragraph);
        cell.setBorder(0);
        cell.setPadding(4);
        return cell;
    }

    private ClienteDTO clienteDe(String rut) {
        try {
            return clienteService.obtener(rut);
        } catch (ApiException e) {
            return null;
        }
    }

    private String condicion(String value) {
        if (value == null) {
            return "NETO_30";
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "CONTADO" -> "Contado";
            case "NETO_60" -> "Neto 60 dias";
            default -> "Neto 30 dias";
        };
    }

    private String moneda(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return financeProperties.getCurrency() + " " + format.format(safe);
    }

    private String valor(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
