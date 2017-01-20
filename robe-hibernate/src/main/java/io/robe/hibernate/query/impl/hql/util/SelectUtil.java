package io.robe.hibernate.query.impl.hql.util;

import io.robe.common.utils.Validations;
import io.robe.hibernate.query.api.criteria.Criteria;
import io.robe.hibernate.query.api.criteria.CriteriaJoin;
import io.robe.hibernate.query.api.criteria.CriteriaParent;
import io.robe.hibernate.query.api.criteria.cache.EntityMeta;
import io.robe.hibernate.query.api.criteria.cache.FieldMeta;
import io.robe.hibernate.query.api.criteria.projection.*;
import io.robe.hibernate.query.api.query.Query;
import io.robe.hibernate.query.api.query.Transformer;
import io.robe.hibernate.query.impl.hql.TransformerImpl;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Created by kamilbukum on 17/01/2017.
 */
public class SelectUtil {
    /**
     *
     * @param criteria
     * @param <E>
     * @return
     */
    public static <E> String generateSelectQueryForList(Criteria<E> criteria, TransformerImpl transformer) {
        if(transformer.getTransformType() == TransformerImpl.TransformType.DTO) {
            return generateDTOSelect(criteria, transformer);
        }
        String select = selectForListRecursively(criteria);
        if("".equals(select)) {
            return criteria.getAlias();
        }
        return select;
    }

    public static <E> String  generateDTOSelect(Criteria<E> criteria, TransformerImpl<E> transformer){
        StringJoiner joiner = new StringJoiner(", ");
        EntityMeta joinMeta  = Query.CachedEntity.getEntityMeta(criteria.getEntityClass(), transformer.getFinder());
        for(Map.Entry<String, FieldMeta> fieldEntry: joinMeta.getFieldMap().entrySet()) {
            if(fieldEntry.getValue().hasRelation()) {
                String relation = joinMeta.getFieldRelationMap().get(fieldEntry.getKey());
                joiner.add(relation + " AS " + fieldEntry.getKey());
            } else if(!fieldEntry.getValue().isTransient()) {
                joiner.add(criteria.getAlias() + "." + fieldEntry.getKey() + " AS " + fieldEntry.getKey());
            }
        }
        return joiner.toString();
    }
    /**
     *
     * @param criteria
     * @return
     */
    private static String selectForListRecursively(CriteriaParent criteria){
        StringJoiner joiner = new StringJoiner(", ");
        if(criteria.getProjection() != null) {
            joiner.add(selectForListByProjection(criteria, criteria.getProjection(), null));
        }
        for(Map.Entry<String, CriteriaJoin> joinEntry: criteria.getJoins().entrySet()) {
            String joinProjectionResult = selectForListRecursively(joinEntry.getValue());
            if(!Validations.isEmptyOrNull(joinProjectionResult)) {
                joiner.add(joinProjectionResult);
            }
        }
        return joiner.toString();
    }

    /**
     *
     * @param criteria
     * @param projection
     * @return
     */
    private static String selectForListByProjection(CriteriaParent criteria, Projection projection, String alias){
        if(projection instanceof IdentifierProjection) {
            return criteria.getAlias() + "." + criteria.getIdentityName() + getAsKey(criteria.getAlias(), criteria.getIdentityName(), alias);
        }
        if(projection instanceof PropertyProjection) {
            PropertyProjection p = (PropertyProjection)projection;
            return criteria.getAlias() + "." + p.getProperty() + getAsKey(criteria.getAlias(), p.getProperty(), alias);
        } else if(projection instanceof FunctionProjection) {
            FunctionProjection pp = (FunctionProjection)projection;
            if(FunctionProjection.Type.COUNT == pp.getFnType() && Validations.isEmptyOrNull(pp.getProperty())) {
                return pp.getFnType().name() + "(1)" + getAsKey(criteria.getAlias(), pp.getFnType().name().toLowerCase(), alias);
            } else {
                return pp.getFnType().name() + "(" + criteria.getAlias() + "." + pp.getProperty() + ")" + getAsKey(criteria.getAlias(), pp.getFnType().name().toLowerCase(), alias);
            }
        } else if(projection instanceof EnhancedProjection) {
            EnhancedProjection p = (EnhancedProjection)projection;
            return selectForListByProjection(criteria, p.getProjection(), p.getAlias());
        } else if(projection instanceof ProjectionList){
            ProjectionList p = (ProjectionList)projection;
            StringJoiner joiner = new StringJoiner(", ");
            for(int i = 0 ; i < p.getLength(); i++) {
                Projection childProjection = p.getProjection(i);
                joiner.add(selectForListByProjection(criteria, childProjection, null));
            }
            return joiner.toString();
        } else {
            throw new RuntimeException("Unknown Projection !"+ projection);
        }
    }

    private static String getAsKey(String criteriaAlias, String selectAlias, String asAlias){

        String alias = asAlias != null ? asAlias.replaceAll("\\.", "_0_") : (criteriaAlias + "_0_" + selectAlias);


        return  " AS "  + alias;
    }

}