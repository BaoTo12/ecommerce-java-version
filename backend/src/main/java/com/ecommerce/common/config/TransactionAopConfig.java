package com.ecommerce.common.config;

import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.TransactionDefinition;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class TransactionAopConfig {

    @Bean
    public TransactionInterceptor txAdvice(TransactionManager transactionManager) {
        NameMatchTransactionAttributeSource txAttributeSource = new NameMatchTransactionAttributeSource();

        // 1. Read-only Transaction Attribute
        RuleBasedTransactionAttribute readOnlyAttr = new RuleBasedTransactionAttribute();
        readOnlyAttr.setReadOnly(true);

        // 2. Read-write (Required) Transaction Attribute
        RuleBasedTransactionAttribute requiredAttr = new RuleBasedTransactionAttribute();
        requiredAttr.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        // 3. Requires New Transaction Attribute (for checkout steps)
        RuleBasedTransactionAttribute requiresNewAttr = new RuleBasedTransactionAttribute();
        requiresNewAttr.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Map method names to transaction attributes
        Map<String, TransactionAttribute> txMethods = new HashMap<>();
        
        // Read operations
        txMethods.put("get*", readOnlyAttr);
        txMethods.put("find*", readOnlyAttr);
        txMethods.put("list*", readOnlyAttr);
        txMethods.put("search*", readOnlyAttr);

        // Requires new operations
        txMethods.put("prepare*", requiresNewAttr);
        txMethods.put("finalize*", requiresNewAttr);

        // Default write operations
        txMethods.put("*", requiredAttr);

        txAttributeSource.setNameMap(txMethods);

        return new TransactionInterceptor(transactionManager, txAttributeSource);
    }

    @Bean
    public Advisor txAdviceAdvisor(TransactionInterceptor txAdvice) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        // Target all domain services
        pointcut.setExpression("execution(* com.ecommerce.domain..domain.service..*(..))");
        return new DefaultPointcutAdvisor(pointcut, txAdvice);
    }
}
