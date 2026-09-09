package com.github.beng420.kung.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class UiTextField {
    private final EditBox control;

    public UiTextField(Font font, Component narration) {
        control = new EditBox(font, 0, 0, narration);
    }

    public UiTextField maxLength(int maxLength) {
        control.setMaxLength(maxLength);
        return this;
    }

    public UiTextField textShadow(boolean textShadow) {
        control.setTextShadow(textShadow);
        return this;
    }

    public UiTextField canLoseFocus(boolean canLoseFocus) {
        control.setCanLoseFocus(canLoseFocus);
        return this;
    }

    public void setBounds(int x, int y, int width, int height) {
        control.setX(x);
        control.setY(y);
        control.setWidth(width);
        control.setHeight(height);
    }

    public UiBounds bounds() {
        return new UiBounds(control.getX(), control.getY(), control.getWidth(), control.getHeight());
    }

    public boolean contains(int x, int y) {
        return bounds().contains(x, y);
    }

    public int x() {
        return control.getX();
    }

    public int width() {
        return control.getWidth();
    }

    public String value() {
        return control.getValue();
    }

    public void setValue(String value) {
        control.setValue(value == null ? "" : value);
    }

    public boolean isFocused() {
        return control.isFocused();
    }

    public void setFocused(boolean focused) {
        control.setFocused(focused);
    }

    public void moveCursorToEnd() {
        control.moveCursorToEnd(false);
    }

    public void extractRenderState(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        control.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return control.mouseClicked(event, doubleClick);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return control.mouseDragged(event, dragX, dragY);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return control.mouseReleased(event);
    }

    public boolean keyPressed(KeyEvent event) {
        return control.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        return control.charTyped(event);
    }
}
