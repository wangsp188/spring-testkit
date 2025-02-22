package com.testkit.view;

import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

// 自定义单元格渲染器，实现自动换行
class TextAreaCellRenderer extends JTextArea implements TableCellRenderer {
    private Set<Integer> tooltips = new HashSet<>();

    public TextAreaCellRenderer(int row, Set<Integer> tooltips) {
        setLineWrap(true); // 自动换行
        setWrapStyleWord(true); // 按单词换行
        setOpaque(true); // 设置不透明
        setRows(row);
        if (tooltips != null) {
            this.tooltips.addAll(tooltips);
        }
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        setText((value == null) ? "" : value.toString());
        if (tooltips.contains(column)) {
            String str = value != null ? value.toString() : "";
            List<String> strs = Arrays.asList(str.replace("\n", "<br>").split("<br>"));
            if (strs.size() > 20) {
                strs = new ArrayList<>(strs.subList(0, 19));
                strs.add("...");
            }
            setToolTipText(String.join("<br>", strs));
        } else {
            setToolTipText(null);
        }
        return this;
    }
}
