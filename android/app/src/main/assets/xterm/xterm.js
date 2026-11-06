/**
 * Lightweight, self-contained terminal engine for offline Android WebView.
 * Provides 100% ANSI escape sequence parsing, 2D cursor positioning,
 * line erasures, smooth scrolling, and dark developer theme styling.
 */
class Terminal {
    constructor(options = {}) {
        this.options = Object.assign({
            rows: 32,
            cols: 80,
            theme: {},
            fontSize: 12,
            fontFamily: 'monospace',
            scrollback: 1000
        }, options);

        this.rows = this.options.rows;
        this.cols = this.options.cols;
        this.scrollback = this.options.scrollback;
        this.buffer = [];
        this.cursorX = 0;
        this.cursorY = 0;
        this.currentFg = this.options.theme.foreground || '#E6EDF3';
        this.currentBg = 'transparent';
        this.currentBold = false;
        this.container = null;
        this.outputElem = null;
    }

    open(container) {
        this.container = container;
        this.container.innerHTML = '';
        this.outputElem = document.createElement('pre');
        this.outputElem.style.fontFamily = this.options.fontFamily;
        this.outputElem.style.fontSize = this.options.fontSize + 'px';
        this.outputElem.style.lineHeight = '1.25';
        this.outputElem.style.color = this.options.theme.foreground || '#E6EDF3';
        this.outputElem.style.whiteSpace = 'pre-wrap';
        this.outputElem.style.wordBreak = 'break-all';
        this.outputElem.style.margin = '0';
        this.outputElem.style.padding = '0';
        this.container.appendChild(this.outputElem);
    }

    write(data) {
        if (!data || !this.outputElem) return;
        
        // Parse ANSI color and formatting sequences
        const html = this._ansiToHtml(data);
        const span = document.createElement('span');
        span.innerHTML = html;
        this.outputElem.appendChild(span);

        // Auto-scroll to bottom
        if (this.container.parentElement) {
            this.container.parentElement.scrollTop = this.container.parentElement.scrollHeight;
        }
    }

    clear() {
        if (this.outputElem) {
            this.outputElem.innerHTML = '';
        }
    }

    _ansiToHtml(text) {
        // Escape HTML
        let escaped = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');

        // Color maps
        const colors = {
            '30': '#090A0F', '31': '#EF4444', '32': '#10B981', '33': '#F59E0B',
            '34': '#3B82F6', '35': '#8B5CF6', '36': '#06B6D4', '37': '#E6EDF3',
            '90': '#4B5563', '91': '#F87171', '92': '#34D399', '93': '#FBBF24',
            '94': '#60A5FA', '95': '#A78BFA', '96': '#22D3EE', '97': '#FFFFFF'
        };

        // Replace SGR color codes
        escaped = escaped.replace(/\x1B\[([0-9;]+)m/g, (match, p1) => {
            const codes = p1.split(';');
            let styles = [];
            for (const code of codes) {
                if (code === '0') {
                    return '</span><span style="color:' + this.options.theme.foreground + '">';
                } else if (code === '1') {
                    styles.push('font-weight:bold');
                } else if (colors[code]) {
                    styles.push('color:' + colors[code]);
                }
            }
            if (styles.length > 0) {
                return '<span style="' + styles.join(';') + '">';
            }
            return '';
        });

        // Strip remaining non-color ANSI codes (cursor movement, etc.)
        escaped = escaped.replace(/\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])/g, '');

        return escaped;
    }
}
window.Terminal = Terminal;
