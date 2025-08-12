package com.example.hikabrain.ui;

import com.example.hikabrain.Arena;
import com.example.hikabrain.ui.model.Theme;
import java.util.Set;

public interface ThemeService {
    void applyTheme(Arena a, String themeId);
    Theme themeOf(Arena a);
    Set<String> available();
}
