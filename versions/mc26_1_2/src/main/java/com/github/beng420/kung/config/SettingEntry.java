package com.github.beng420.kung.config;

import com.github.beng420.kung.ui.UiBounds;
import com.github.beng420.kung.ui.UiNumberField;
import com.github.beng420.kung.ui.UiTheme;
import com.github.beng420.kung.ui.UiToggle;
import com.github.beng420.kung.ui.UiShapes;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public record SettingEntry(
    Supplier<String> labelSupplier,
    SettingKind kind,
    BooleanSupplier booleanSupplier,
    Runnable toggle,
    IntSupplier intSupplier,
    IntConsumer intConsumer,
    int min,
    int max,
    int step,
    Supplier<String> choiceSupplier,
    Runnable cycleChoice,
    Supplier<String> textSupplier,
    Consumer<String> textConsumer,
    List<SettingEntry> children,
    List<String> tooltip,
    List<String> choices,
    Supplier<String> rawKeybindSupplier
) {
    public String label() { return labelSupplier.get(); }

    public static SettingEntry toggle(String label, BooleanSupplier supplier, Runnable toggle) {
        return create(label, SettingKind.TOGGLE, supplier, toggle, null, null, 0, 0, 0, null, null, null, null);
    }

    public static SettingEntry stepper(
        String label,
        IntSupplier supplier,
        IntConsumer consumer,
        int min,
        int max,
        int step
    ) {
        return create(label, SettingKind.STEPPER, null, null, supplier, consumer, min, max, step, null, null, null, null);
    }

    public static SettingEntry slider(
        String label,
        IntSupplier supplier,
        IntConsumer consumer,
        int min,
        int max,
        int step
    ) {
        return create(label, SettingKind.SLIDER, null, null, supplier, consumer, min, max, step, null, null, null, null);
    }

    public static SettingEntry stepperRemove(String label, IntSupplier supplier, IntConsumer consumer,
                                             int min, int max, int step, Runnable remove) {
        return create(label, SettingKind.STEPPER_REMOVE, null, null, supplier, consumer,
            min, max, step, null, remove, null, null);
    }

    public static SettingEntry choice(String label, Supplier<String> supplier, Runnable cycle) {
        return create(label, SettingKind.CHOICE, null, null, null, null, 0, 0, 0, supplier, cycle, null, null);
    }

    public static <E extends Enum<E>> SettingEntry choice(
        String label, E[] values, Supplier<E> supplier, Consumer<E> consumer, Function<E, String> labeler
    ) {
        List<E> options = List.of(values);
        if (options.isEmpty()) throw new IllegalArgumentException("Choice must have options");
        IntSupplier selected = () -> Math.max(0, options.indexOf(supplier.get()));
        IntConsumer select = index -> {
            if (index >= 0 && index < options.size()) consumer.accept(options.get(index));
        };
        return new SettingEntry(() -> label, SettingKind.CHOICE, null, null, selected, select,
            0, options.size() - 1, 1, () -> labeler.apply(options.get(selected.getAsInt())),
            () -> select.accept((selected.getAsInt() + 1) % options.size()), null, null, List.of(), List.of(),
            options.stream().map(labeler).toList(), null);
    }

    public static SettingEntry text(String label, Supplier<String> supplier, Consumer<String> consumer) {
        return create(label, SettingKind.TEXT, null, null, null, null, 0, 0, 0, null, null, supplier, consumer);
    }

    public static SettingEntry group(String label) {
        return create(label, SettingKind.GROUP, null, null, null, null, 0, 0, 0, null, null, null, null);
    }

    public static SettingEntry keybind(String label, Supplier<String> supplier, Consumer<String> consumer) {
        return keybind(label, supplier, supplier, consumer);
    }

    public static SettingEntry keybind(String label, Supplier<String> display, Supplier<String> raw,
                                      Consumer<String> consumer) {
        return new SettingEntry(() -> label, SettingKind.KEYBIND, null, null, null, null, 0, 0, 0,
            null, null, display, consumer, List.of(), List.of(), List.of(), raw);
    }

    public static SettingEntry dynamicLabel(Supplier<String> labelSupplier) {
        return new SettingEntry(labelSupplier, SettingKind.LABEL, null, null, null, null, 0, 0, 0,
            null, null, null, null, List.of(), List.of(), List.of(), null);
    }

    public static SettingEntry button(String label, String text, Runnable action) {
        return create(label, SettingKind.BUTTON, null, action, null, null, 0, 0, 0, () -> text, null, null, null);
    }

    public static SettingEntry colorRemove(String label, IntSupplier color, Runnable edit, Runnable remove) {
        return create(label, SettingKind.COLOR_REMOVE, null, edit, color, null, 0, 0, 0, () -> "Edit", remove, null, null);
    }

    public SettingEntry withChildren(List<SettingEntry> children) {
        return new SettingEntry(labelSupplier, kind, booleanSupplier, toggle, intSupplier, intConsumer,
            min, max, step, choiceSupplier, cycleChoice, textSupplier, textConsumer, List.copyOf(children), tooltip,
            choices, rawKeybindSupplier);
    }

    public SettingEntry withTooltip(String... lines) {
        return new SettingEntry(labelSupplier, kind, booleanSupplier, toggle, intSupplier, intConsumer,
            min, max, step, choiceSupplier, cycleChoice, textSupplier, textConsumer, children, List.of(lines),
            choices, rawKeybindSupplier);
    }

    public String textValue() {
        return textSupplier == null ? "" : textSupplier.get();
    }

    public boolean expandable() {
        return !children.isEmpty();
    }

    public void draw(
        GuiGraphicsExtractor graphics,
        Font font,
        UiTheme theme,
        int x,
        int y,
        int width,
        int height,
        boolean capturing
    ) {
        switch (kind) {
            case TOGGLE -> UiToggle.draw(
                graphics,
                new UiBounds(x + width - 22, y, 22, height),
                booleanSupplier.getAsBoolean(),
                theme
            );
            case STEPPER -> drawStepper(graphics, font, theme, x, y, width, height);
            case STEPPER_REMOVE -> {
                drawStepper(graphics, font, theme, x, y, width - 19, height);
                drawRemoveButton(graphics, font, theme, x, y, width, height);
            }
            case SLIDER -> drawSlider(graphics, font, theme, x, y, width, height);
            case CHOICE, BUTTON -> drawButton(graphics, font, theme, x, y, width, height, choiceSupplier.get());
            case COLOR_REMOVE -> {
                UiShapes.rounded(graphics, x + 1, y + 3, width - 19, height - 6, 2, intSupplier.getAsInt());
                drawRemoveButton(graphics, font, theme, x, y, width, height);
            }
            case LABEL -> graphics.text(font, label(), x + width - font.width(label()), y + 4, theme.muted(), true);
            case TEXT -> {
                graphics.fill(x + 1, y + 3, x + width - 1, y + height - 3, theme.accentDark());
                graphics.text(font, font.plainSubstrByWidth(textSupplier.get(), width - 8), x + 4, y + 4, theme.text(), true);
            }
            case KEYBIND -> {
                String text = capturing ? "Press..." : textSupplier.get();
                graphics.fill(x + 1, y + 3, x + width - 1, y + height - 3,
                    capturing ? theme.accent() : theme.accentDark());
                String shown = font.plainSubstrByWidth(text, width - 8);
                graphics.text(font, shown, x + width / 2 - font.width(shown) / 2, y + 4, theme.text(), true);
            }
            case GROUP -> { }
        }
    }

    public void click(int mouseX, int x, int width) {
        switch (kind) {
            case TOGGLE, BUTTON -> toggle.run();
            case COLOR_REMOVE -> {
                if (mouseX >= x + width - 16) cycleChoice.run();
                else if (mouseX >= x) toggle.run();
            }
            case STEPPER -> {
                UiNumberField number = numberField();
                intConsumer.accept(mouseX < x + width / 2
                    ? number.decrement(intSupplier.getAsInt())
                    : number.increment(intSupplier.getAsInt()));
            }
            case STEPPER_REMOVE -> {
                if (mouseX < x || mouseX >= x + width) return;
                if (mouseX >= x + width - 16) cycleChoice.run();
                else if (mouseX < x + width - 19) {
                    intConsumer.accept(mouseX < x + (width - 19) / 2
                        ? numberField().decrement(intSupplier.getAsInt())
                        : numberField().increment(intSupplier.getAsInt()));
                }
            }
            case SLIDER -> {
                int trackLeft = x + 2;
                int trackRight = x + width - 2;
                double progress = trackRight <= trackLeft ? 0.0 : (double) (mouseX - trackLeft) / (trackRight - trackLeft);
                intConsumer.accept(numberField().valueAt(progress));
            }
            case CHOICE -> cycleChoice.run();
            case TEXT, KEYBIND, LABEL, GROUP -> { }
        }
    }

    public void appendText(String text) {
        if (kind != SettingKind.TEXT || textConsumer == null || text == null) return;
        String current = textSupplier.get();
        if (current.length() + text.length() <= 120) textConsumer.accept(current + text);
    }

    public void backspaceText() {
        if (kind != SettingKind.TEXT || textConsumer == null) return;
        String current = textSupplier.get();
        if (!current.isEmpty()) textConsumer.accept(current.substring(0, current.length() - 1));
    }

    public void setText(String text) {
        if ((kind == SettingKind.TEXT || kind == SettingKind.KEYBIND) && textConsumer != null) {
            textConsumer.accept(text);
        }
    }

    private void drawStepper(GuiGraphicsExtractor graphics, Font font, UiTheme theme, int x, int y, int width, int height) {
        graphics.fill(x + 1, y + 3, x + 13, y + height - 3, theme.accentDark());
        graphics.fill(x + width - 13, y + 3, x + width - 1, y + height - 3, theme.accentDark());
        graphics.text(font, "-", x + 5, y + 4, theme.text(), true);
        graphics.text(font, "+", x + width - 10, y + 4, theme.text(), true);
        String suffix = kind == SettingKind.STEPPER_REMOVE ? "t"
            : label().toLowerCase(Locale.ROOT).matches(".*(scale|alpha).*") ? "%" : "";
        centered(graphics, font, intSupplier.getAsInt() + suffix, x, y, width, theme.warning());
    }

    private static void drawRemoveButton(GuiGraphicsExtractor graphics, Font font, UiTheme theme,
                                         int x, int y, int width, int height) {
        UiShapes.rounded(graphics, x + width - 15, y + 2, 14, height - 4, 2, theme.panelDark());
        graphics.text(font, "-", x + width - 11, y + 4, theme.error(), false);
    }

    private void drawSlider(GuiGraphicsExtractor graphics, Font font, UiTheme theme, int x, int y, int width, int height) {
        int trackLeft = x + 2;
        int trackRight = x + width - 2;
        int knobX = trackLeft + (int) Math.round(numberField().progress(intSupplier.getAsInt()) * (trackRight - trackLeft));
        int trackY = y + height / 2 - 1;
        graphics.fill(trackLeft, trackY, trackRight, trackY + 2, theme.accentDark());
        graphics.fill(trackLeft, trackY, knobX, trackY + 2, theme.accent());
        graphics.fill(knobX - 2, y + 3, knobX + 2, y + height - 3, theme.text());
        String lower = label().toLowerCase(Locale.ROOT);
        String value = lower.contains("scale") || lower.contains("alpha")
            ? intSupplier.getAsInt() + "%"
            : lower.contains("volume")
            ? String.format(Locale.ROOT, "x%.1f", intSupplier.getAsInt() / 10.0)
            : lower.contains("pitch")
                ? String.format(Locale.ROOT, "x%.2f", intSupplier.getAsInt() / 100.0)
                : String.format(Locale.ROOT, "%.1fs", intSupplier.getAsInt() / 10.0);
        centered(graphics, font, value, x, y, width, theme.warning());
    }

    private static void drawButton(
        GuiGraphicsExtractor graphics,
        Font font,
        UiTheme theme,
        int x,
        int y,
        int width,
        int height,
        String text
    ) {
        UiShapes.rounded(graphics, x + 1, y + 2, width - 2, height - 4, 2, theme.accentDark());
        centered(graphics, font, text, x, y, width, theme.text());
    }

    private static void centered(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int width, int color) {
        graphics.text(font, text, x + width / 2 - font.width(text) / 2, y + 4, color, true);
    }

    private UiNumberField numberField() {
        return new UiNumberField(min, max, step);
    }

    private static SettingEntry create(
        String label,
        SettingKind kind,
        BooleanSupplier booleanSupplier,
        Runnable toggle,
        IntSupplier intSupplier,
        IntConsumer intConsumer,
        int min,
        int max,
        int step,
        Supplier<String> choiceSupplier,
        Runnable cycleChoice,
        Supplier<String> textSupplier,
        Consumer<String> textConsumer
    ) {
        return new SettingEntry(() -> label, kind, booleanSupplier, toggle, intSupplier, intConsumer,
            min, max, step, choiceSupplier, cycleChoice, textSupplier, textConsumer, List.of(), List.of(), List.of(), null);
    }
}
