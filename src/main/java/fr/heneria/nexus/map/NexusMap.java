package fr.heneria.nexus.map;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NexusMap {
    private final String id;
    private final String name;
    private final String description;
    private final String sourceFolder;
}
