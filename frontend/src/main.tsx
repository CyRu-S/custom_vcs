import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import '@fontsource-variable/outfit'
import '@fontsource-variable/public-sans'
import '@fontsource-variable/jetbrains-mono'
import { StoreProvider } from './store'
import App from './App'
import './style.css'
import './polish.css'
import './connected.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <StoreProvider>
        <App />
      </StoreProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
