import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private darkMode = new BehaviorSubject<boolean>(this.getStoredTheme());
  isDarkMode$ = this.darkMode.asObservable();

  constructor() {
    this.applyTheme(this.darkMode.value);
  }

  toggle(): void {
    const newValue = !this.darkMode.value;
    this.darkMode.next(newValue);
    localStorage.setItem('darkMode', JSON.stringify(newValue));
    this.applyTheme(newValue);
  }

  isDark(): boolean {
    return this.darkMode.value;
  }

  private applyTheme(dark: boolean): void {
    document.body.classList.toggle('dark-theme', dark);
  }

  private getStoredTheme(): boolean {
    const stored = localStorage.getItem('darkMode');
    return stored ? JSON.parse(stored) : false;
  }
}
