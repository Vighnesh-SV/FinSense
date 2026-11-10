package com.finsense.model;
/** Current and prior month spend for one category plus its signed percentage change. */
public record CategorySpending(String categoryName,Money current,Money previous,int changePercent,String nature) { }
