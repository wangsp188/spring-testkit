package com.testkit.view;


import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

// 自定义单元格渲染器，实现自动换行
class TextAreaCellRenderer extends JTextArea implements TableCellRenderer {
    private static final Color[] TECH_COLORS = {
//            new Color(207, 201, 201, 255),
//            new Color(240, 255, 220),
            new Color(178, 198, 11, 255),
            new Color(255, 75, 219),
    };


    private final Set<Integer> tooltips;
    private final int randomBackLoop;

    // 添加构造参数控制颜色重复周期
    public TextAreaCellRenderer(int row, Set<Integer> tooltips, int randomBackLoop) {
        setLineWrap(true);
        setWrapStyleWord(true);
        setOpaque(true);
        setRows(row);
        this.tooltips = tooltips != null ? new HashSet<>(tooltips) : Collections.emptySet();
        this.randomBackLoop = randomBackLoop;
    }

    public TextAreaCellRenderer(int row, Set<Integer> tooltips) {
        this(row, tooltips, 0);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
        // 原始文本处理逻辑
        setText(value != null ? value.toString() : "");
        handleTooltip(value, column);

        if (randomBackLoop > 0) {
            int colorIndex = (row / randomBackLoop) % TECH_COLORS.length;
            setForeground(TECH_COLORS[colorIndex]);
//            setBackground(TECH_COLORS[colorIndex]);
        }
        return this;
    }

    // 原有工具提示逻辑
    private void handleTooltip(Object value, int column) {
        if (tooltips.contains(column)) {
            String str = value != null ? value.toString() : "";
            List<String> strs = Arrays.asList(str.replace("\n", "<br>").split("br>"));
            if (strs.size() > 10) {
                strs = new ArrayList<>(strs.subList(0, 9));
                strs.add("...");
            }
            setToolTipText(String.join("<br>", strs));
        } else {
            setToolTipText(null);
        }
    }
}
