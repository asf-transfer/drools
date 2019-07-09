package org.drools.model.impl;

import org.drools.model.Argument;
import org.drools.model.Query2Def;
import org.drools.model.Variable;
import org.drools.model.view.QueryCallViewItem;
import org.drools.model.view.QueryCallViewItemImpl;
import static org.drools.model.FlowDSL.declarationOf;
import static org.drools.model.impl.RuleBuilder.DEFAULT_PACKAGE;

public class Query2DefImpl<T1, T2> extends QueryDefImpl implements ModelComponent, Query2Def<T1, T2> {

    private final Variable<T1> arg1;

    private final Variable<T2> arg2;

    public Query2DefImpl(ViewBuilder viewBuilder, String name, Class<T1> type1, Class<T2> type2) {
        this(viewBuilder, DEFAULT_PACKAGE, name, type1, type2);
    }

    public Query2DefImpl(ViewBuilder viewBuilder, String name, String pkg, Class<T1> type1, Class<T2> type2) {
        super(viewBuilder, pkg, name);
        this.arg1 = declarationOf(type1);
        this.arg2 = declarationOf(type2);
    }

    public Query2DefImpl(ViewBuilder viewBuilder, String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name) {
        this(viewBuilder, DEFAULT_PACKAGE, name, type1, arg1name, type2, arg2name);
    }

    public Query2DefImpl(ViewBuilder viewBuilder, String pkg, String name, Class<T1> type1, String arg1name, Class<T2> type2, String arg2name) {
        super(viewBuilder, pkg, name);
        this.arg1 = declarationOf(type1, arg1name);
        this.arg2 = declarationOf(type2, arg2name);
    }

    @Override()
    public QueryCallViewItem call(boolean open, Argument<T1> var1, Argument<T2> var2) {
        return new QueryCallViewItemImpl(this, open, var1, var2);
    }

    @Override()
    public Variable<?>[] getArguments() {
        return new Variable<?>[] { arg1, arg2 };
    }

    @Override()
    public Variable<T1> getArg1() {
        return arg1;
    }

    @Override()
    public Variable<T2> getArg2() {
        return arg2;
    }

    @Override
    public boolean isEqualTo(ModelComponent other) {
        if (this == other)
            return true;
        if (!(other instanceof Query2DefImpl))
            return false;
        Query2DefImpl that = (Query2DefImpl) other;
        return true && ModelComponent.areEqualInModel(arg1, that.arg1) && ModelComponent.areEqualInModel(arg2, that.arg2);
    }
}
