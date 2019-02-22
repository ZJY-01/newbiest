package com.newbiest.mms.state.model;

import com.newbiest.commom.sm.model.StatusCategory;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Created by guoxunbo on 2019/2/12.
 */
@Entity
@DiscriminatorValue(MaterialStatusCategory.CATEGORY_MATERIAL)
public class MaterialStatusCategory extends StatusCategory {

    public static final String CATEGORY_MATERIAL = "MATERIAL";


}