package com.finsense.decision;

import com.finsense.model.Category;
import com.finsense.model.Money;
import com.finsense.model.enums.Necessity;

/** Validated purchase inputs handed to each polymorphic decision rule. */
public record PurchaseRequest(String productName,Category category,Money price,Necessity necessity,String description) { }
