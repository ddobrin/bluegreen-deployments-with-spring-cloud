package org.springframework.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public class ColorProperties {

	private String color;

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}
}
