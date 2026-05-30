package com.kubiki.palamedes.model;

import org.eclipse.rdf4j.model.IRI;

public record ImageInTopology(
        IRI imageIri,
        String imageRepository,
        String version
) {
}
