import { createApp } from './app';
import { config } from './config';

const app = createApp();

app.listen(config.port, () => {
  console.log(`====================================================`);
  console.log(`  ResQhunT Emergency Rescue Backend is Online       `);
  console.log(`  Listening on port: ${config.port}                `);
  console.log(`  Environment: ${config.nodeEnv}                   `);
  console.log(`  API Health: http://localhost:${config.port}/api/health `);
  console.log(`====================================================`);
});
