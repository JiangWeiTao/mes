/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 1.4
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.mes.basic.hooks;

import static com.qcadoo.mes.basic.constants.ProductFamilyElementType.PARTICULAR_PRODUCT;

import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qcadoo.mes.basic.ProductService;
import com.qcadoo.mes.basic.constants.AdditionalCodeFields;
import com.qcadoo.mes.basic.constants.BasicConstants;
import com.qcadoo.mes.basic.constants.ModelFields;
import com.qcadoo.mes.basic.constants.ProductFamilyElementType;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.basic.tree.ProductNumberingService;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.model.constants.UnitConversionItemFields;

@Service
public class ProductHooks {

    @Autowired
    private ProductNumberingService productNumberingService;

    @Autowired
    private ProductService productService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    public void onSave(final DataDefinition productDD, final Entity product) {
        updateModelAndAssortment(productDD, product);
        updateNodeNumber(productDD, product);
        clearFamilyFromProductWhenTypeIsChanged(productDD, product);
    }

    private void updateModelAndAssortment(final DataDefinition productDD, final Entity product) {
        Long productId = product.getId();
        Entity assortment = product.getBelongsToField(ProductFields.ASSORTMENT);
        Entity model = product.getBelongsToField(ProductFields.MODEL);

        boolean isAssortmentAndModelNonNull = Objects.nonNull(assortment) && Objects.nonNull(model);

        if (Objects.nonNull(productId)) {
            Entity productFromDB = productDD.get(productId);

            Entity assortmentFromDB = productFromDB.getBelongsToField(ProductFields.ASSORTMENT);

            boolean areSame = (Objects.isNull(assortment) ? Objects.isNull(assortmentFromDB)
                    : (Objects.nonNull(assortmentFromDB) ? assortment.getId().equals(assortmentFromDB.getId()) : false));

            if (areSame) {
                if (Objects.nonNull(model) && Objects.isNull(assortment)) {
                    Entity modelAssortment = product.getBelongsToField(ProductFields.MODEL_ASSORTMENT);

                    if (Objects.isNull(modelAssortment)) {
                        modelAssortment = model.getBelongsToField(ModelFields.ASSORTMENT);
                    }

                    product.setField(ProductFields.ASSORTMENT, modelAssortment);
                }
            } else {
                if (isAssortmentAndModelNonNull) {
                    Entity modelAssortment = product.getBelongsToField(ProductFields.MODEL_ASSORTMENT);

                    if (Objects.isNull(modelAssortment)) {
                        modelAssortment = model.getBelongsToField(ModelFields.ASSORTMENT);
                    }

                    if (Objects.nonNull(modelAssortment) && !modelAssortment.getId().equals(assortment.getId())) {
                        product.setField(ProductFields.MODEL, null);
                    }
                }
            }
        } else {
            if (isAssortmentAndModelNonNull) {
                Entity modelAssortment = model.getBelongsToField(ModelFields.ASSORTMENT);

                if (Objects.nonNull(modelAssortment) && !modelAssortment.getId().equals(assortment.getId())) {
                    product.setField(ProductFields.MODEL, null);
                }
            }
        }
    }

    public void generateNodeNumber(final DataDefinition productDD, final Entity product) {
        productNumberingService.generateNodeNumber(product);
    }

    public void updateNodeNumber(final DataDefinition productDD, final Entity product) {
        productNumberingService.updateNodeNumber(product);
    }

    public void clearFamilyFromProductWhenTypeIsChanged(final DataDefinition productDD, final Entity product) {
        if (Objects.isNull(product.getId())) {
            return;
        }

        String entityType = product.getStringField(ProductFields.ENTITY_TYPE);
        Entity productFromDB = product.getDataDefinition().get(product.getId());

        if (entityType.equals(PARTICULAR_PRODUCT.getStringValue())
                && !entityType.equals(productFromDB.getStringField(ProductFields.ENTITY_TYPE))) {
            deleteProductFamily(productDD, productFromDB);
        }
    }

    private void deleteProductFamily(final DataDefinition productDD, final Entity product) {
        List<Entity> productsWithFamily = productDD.find().add(SearchRestrictions.belongsTo(ProductFields.PARENT, product)).list()
                .getEntities();

        for (Entity entity : productsWithFamily) {
            entity.setField(ProductFields.PARENT, null);
            productDD.save(entity);
        }
    }

    public boolean checkIfNotBelongsToSameFamily(final DataDefinition productDD, final Entity product) {
        if (Objects.nonNull(product.getId())) {
            Entity parent = product.getBelongsToField(ProductFields.PARENT);

            if (Objects.nonNull(parent) && product.getId().equals(parent.getId())) {
                product.addError(productDD.getField(ProductFields.PARENT), "basic.product.parent.belongsToSameFamily");

                return false;
            }
        }

        return true;
    }

    public boolean checkIfParentIsFamily(final DataDefinition productDD, final Entity product) {
        Entity parent = product.getBelongsToField(ProductFields.PARENT);

        if (Objects.isNull(parent)) {
            return true;
        }

        if (ProductFamilyElementType.PRODUCTS_FAMILY.getStringValue().equals(parent.getStringField(ProductFields.ENTITY_TYPE))) {
            return true;
        } else {
            product.addError(productDD.getField(ProductFields.PARENT), "basic.product.parent.parentIsNotFamily");

            return false;
        }
    }

    public void clearFieldsOnCopy(final DataDefinition productDD, final Entity product) {
        if (Objects.isNull(product)) {
            return;
        }

        product.setField(ProductFields.ADDITIONAL_UNIT, null);
        product.setField(ProductFields.EAN, null);
    }

    public void clearExternalIdOnCopy(final DataDefinition productDD, final Entity product) {
        if (Objects.isNull(product)) {
            return;
        }

        product.setField(ProductFields.EXTERNAL_NUMBER, null);
    }

    public void calculateConversionIfUnitChanged(final DataDefinition productDD, final Entity product) {
        if (productService.hasUnitChangedOnUpdate(product)) {
            productService.conversionForProductUnit(product);
        }
    }

    public void calculateConversionOnCreate(final DataDefinition productDD, final Entity product) {
        productService.conversionForProductUnit(product);
    }

    public boolean validateAdditionalUnit(final DataDefinition productDD, final Entity product) {
        String additionalUnit = product.getStringField(ProductFields.ADDITIONAL_UNIT);
        String defaultUnit = product.getStringField(ProductFields.UNIT);

        if (!StringUtils.isEmpty(additionalUnit)) {
            if (additionalUnit.equals(defaultUnit)) {
                product.addError(productDD.getField(ProductFields.ADDITIONAL_UNIT),
                        "basic.product.additionalUnit.error.sameUnits");

                return false;
            }

            List<Entity> conversions = product.getHasManyField(ProductFields.CONVERSION_ITEMS);

            if (!conversions.stream().anyMatch(
                    conversionItem -> conversionItem.getStringField(UnitConversionItemFields.UNIT_TO).equals(additionalUnit)
                            && conversionItem.getStringField(UnitConversionItemFields.UNIT_FROM).equals(defaultUnit))) {
                product.addGlobalError("basic.product.additionalUnit.error.unitConversionMissing");

                return false;
            }
        }

        return true;
    }

    public boolean validateCodeUniqueness(final DataDefinition productDD, final Entity product) {
        Entity duplicateCode = getAdditionalCodeDD().find()
                .add(SearchRestrictions.eq(AdditionalCodeFields.CODE, product.getStringField(ProductFields.NUMBER)))
                .setMaxResults(1).uniqueResult();

        if (Objects.nonNull(duplicateCode)) {
            product.addError(productDD.getField(ProductFields.NUMBER), "qcadooView.validate.field.error.duplicated");

            return false;
        }

        return true;
    }

    private DataDefinition getAdditionalCodeDD() {
        return dataDefinitionService.get(BasicConstants.PLUGIN_IDENTIFIER, BasicConstants.MODEL_ADDITIONAL_CODE);
    }

}
