package com.github.beng420.kung.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class SettingEntryTest {
    @Test
    public void generatesTypedSettingsAndAppliesValidatedValues() {
        AtomicBoolean enabled = new AtomicBoolean();
        SettingEntry toggle = SettingEntry.toggle("Enabled", enabled::get, () -> enabled.set(!enabled.get()));
        toggle.click(0, 0, 50);
        assertTrue(enabled.get());

        AtomicInteger value = new AtomicInteger(10);
        SettingEntry stepper = SettingEntry.stepper("Scale", value::get, value::set, 10, 20, 5);
        stepper.click(49, 0, 50);
        stepper.click(49, 0, 50);
        stepper.click(49, 0, 50);
        assertEquals(20, value.get());

        AtomicReference<String> text = new AtomicReference<>("");
        SettingEntry editor = SettingEntry.text("Name", text::get, text::set);
        editor.appendText("Room");
        editor.backspaceText();
        assertEquals("Roo", text.get());

        assertFalse(editor.expandable());
        assertTrue(SettingEntry.group("Group").withChildren(List.of(editor)).expandable());
    }
}
