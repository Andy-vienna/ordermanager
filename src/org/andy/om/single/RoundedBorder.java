package org.andy.om.single;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.border.Border;

public class RoundedBorder implements Border {
	private int radius;

	public RoundedBorder(int radius) {
		this.radius = radius;
	}

	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(Color.LIGHT_GRAY); // Farbe des Rahmens
		g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
	}

	@Override
	public Insets getBorderInsets(Component c) {
		return new Insets(4, 4, 4, 4);
	}

	@Override
	public boolean isBorderOpaque() {
		return false;
	}
}
