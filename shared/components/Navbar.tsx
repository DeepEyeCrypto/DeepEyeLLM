import React from 'react';
import './Navbar.css';

const Navbar: React.FC = () => {
  return (
    <nav className="deep-navbar">
      <div className="nav-container">
        {/* Brand / Logo */}
        <div className="nav-brand">
          <span className="brand-logo"></span>
          <span className="brand-text">DeepEye</span>
        </div>

        {/* Navigation Links */}
        <ul className="nav-links">
          <li><a href="#home" className="nav-item">Home</a></li>
          <li><a href="#stats" className="nav-item">Typing Stats</a></li>
          <li><a href="#settings" className="nav-item">Settings</a></li>
        </ul>

        {/* Action Button */}
        <div className="nav-actions">
          <button className="btn-primary">Login</button>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
