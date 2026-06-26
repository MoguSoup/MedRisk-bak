package com.hbut.medrisk.dto;

public record PredictionFactorInfo(String name, String label, Object value, double impact, String direction) {
}
