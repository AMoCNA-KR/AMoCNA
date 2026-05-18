package com.kubiki.palamedes.knowledge;

import com.kubiki.daedalus.annotation.Bind;
import com.kubiki.daedalus.annotation.DaedalusRepository;
import com.kubiki.daedalus.annotation.Template;
import com.kubiki.daedalus.annotation.TemplateType;
import com.kubiki.daedalus.annotation.Type;

@DaedalusRepository
public interface TestSparqlRepository {
    @Template(resource = "sparql/query-to-template.sparql")
    String hydrateTestQuery(@Type(TemplateType.IRI) @Bind("IRI::actionId") String actionId);
}
