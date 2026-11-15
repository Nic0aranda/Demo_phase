package com.phase.api.phase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhaseCommandsTest {

    @Test
    void latestChangedBuildsValidSyntax() {
        String command = PhaseCommands.latestChanged("CLIENTES", "76123456-7", 42);
        assertEquals("WRITE $CHANGED(^[42]CLIENTES(\"76123456-7\")),!", command);
    }

    @Test
    void latestChangedWithLeadingCaretBuildsValidSyntax() {
        String command = PhaseCommands.latestChanged("^CLIENTES", "76123456-7", 42);
        assertEquals("WRITE $CHANGED(^[42]CLIENTES(\"76123456-7\")),!", command);
    }

    @Test
    void latestChangedRejectsNegativeTransaction() {
        assertThrows(IllegalArgumentException.class, () ->
                PhaseCommands.latestChanged("CLIENTES", "key", -1)
        );
    }

    @Test
    void readJsonAtBuildsValidCommand() {
        String command = PhaseCommands.readJsonAt("CLIENTES", "76123456-7", 42);
        assertEquals("DO WITH @TX(42) @BIND(^CLIENTES)\n. ZWRITE ^CLIENTES(\"76123456-7\")", command);
    }

    @Test
    void globalAndGlobalName() {
        assertEquals("CLIENTES", PhaseCommands.globalName("CLIENTES"));
        assertEquals("CLIENTES", PhaseCommands.globalName("^CLIENTES"));
        assertEquals("^CLIENTES", PhaseCommands.global("CLIENTES"));
        assertEquals("^CLIENTES", PhaseCommands.global("^CLIENTES"));
    }
}
