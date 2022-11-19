module.exports = {
  content: ['./src/ts/**/*.{ts,pcss}'],
  corePlugins: {
    preflight: false,
  },

  theme: {
    extend: {
      colors: {
        DEFAULT: '#0099cc',
        primary: '#007ACC',
        grey: '#EAEFF2',
        secondary: '#289CE1',
        danger: '#D32F2F',
        success: '#2E7D32',
        note: '#000000DE',
        'trafficlight-green': '#2E7D32',
        'trafficlight-yellow': '#FFA726',
        'trafficlight-red': '#F44336',
        'secondary-button': 'rgb(0, 122, 204)',
        'grey-dark': "#333333",
        'grey-light': "#c8c8c8",
        'tabel-seperator': '#EAEFF2',
        'hover': '#f8f8f8',
        'modal-from': '#0099CC',
        'modal-to': '#007ACC',
        'modal-note': '#757575',
      },
      borderColor: {
        'grey-dark': "#333333",
        'grey-light': "#c8c8c8",
        'tabel-seperator': '#EAEFF2',

      },
      screens: {
        desktop: { min: '1440' },
        laptop: { max: '1366px' },
        tablet: { max: '1024px' },
        phone: { max: '812px' },
      },
      textColor: {
        DEFAULT: '#1E252C',
        primary: '#007ACC',
        secondary: '#289CE1',
        danger: '#D32F2F',
        'grey-light': "#c8c8c8",
        'trafficlight-green': '#148571',
        'trafficlight-yellow': '#A96600',
        'trafficlight-red': '#E22B2F',
      },

      fontFamily: {
        default: [
          '"Roboto"',
        ],
        sans: [
          '"Roboto Sans"',
        ],
        'open-sans': ['"Open Sans"'],
        'source-sans-pro': ['"Source Sans Pro"'],
        montserrat: ['"Montserrat"'],
      },
    },
  },
  variants: {},
  plugins: [
  ],
};
